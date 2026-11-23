"""HTTP assertions for the isolated services launched by run.py."""
import time
import uuid
import requests
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID


def session(root):
    s = requests.Session()
    s.trust_env = False
    s.verify = str(root / 'ca.pem')
    return s


def outcome(s, method, url, body=None):
    r = s.request(method, url, json=body, timeout=12, allow_redirects=False)
    code = r.status_code
    try:
        value = r.json()
    except ValueError:
        value = None
    if 200 <= code < 300 and isinstance(value, dict) and 'code' in value:
        code, value = value['code'], value.get('data')
    return code, value


def call(s, method, url, body=None, *, reject=()):
    code, value = outcome(s, method, url, body)
    if reject:
        assert code in reject, (method, url, code, reject)
    else:
        assert 200 <= code < 300, (method, url, code)
    return value


def gm_login(root, origin, password):
    s = session(root)
    auth = call(s, 'POST', origin + '/api/v1/auth/login', {'username':'interop', 'password':password})
    s.headers['X-CSRF-Token'] = auth['csrfToken']
    return s


def verify(root, gm, fm, pid, bearer, password):
    ui = gm_login(root, gm, password)
    api, bridge, guest = (session(root) for _ in range(3))
    creds = {'username':'admin', 'password':'admin123'}  # Only the new fixture DB
    login = call(api, 'POST', fm + '/api/auth/login', creds)
    api.headers['Authorization'] = 'Bearer ' + login['token']
    bridge.headers['Authorization'] = 'Bearer ' + bearer
    suffix = f'/projects/{pid}/standard-client-certificates'
    routes = [(ui, gm + '/api/v1' + suffix), (api, fm + '/api/admin/standard-client-certificates'),
              (bridge, gm + '/integration/v1' + suffix)]
    for _, url in routes:
        call(guest, 'GET', url, reject=(401,403))
    for i, (writer, url) in enumerate(routes[:2]):
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        label = 'interop-' + str(i)
        csr = x509.CertificateSigningRequestBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,label)])).sign(key, hashes.SHA256())
        body = dict(requestId=str(uuid.uuid4()), deviceLabel=label, csrPem=csr.public_bytes(serialization.Encoding.PEM).decode())
        if i == 0:
            csrf = ui.headers.pop('X-CSRF-Token')
            call(ui, 'POST', url, body, reject=(403,))
            ui.headers['X-CSRF-Token'] = csrf
        row = call(writer, 'POST', url, body)
        rid = row['id']
        assert call(writer, 'POST', url, body)['id'] == rid
        call(writer, 'POST', url, dict(body, deviceLabel=label+'-different'), reject=(409,))
        issued_der = x509.load_pem_x509_certificate(row['certificatePem'].encode()).public_bytes(serialization.Encoding.DER)
        for reader, base in routes:
            page = call(reader, 'GET', base)
            assert page['total'] == len(page['items']) == i+1 and rid in [x['id'] for x in page['items']]
            detail = call(reader, 'GET', base + '/' + str(rid))
            cert = x509.load_pem_x509_certificate(detail['certificatePem'].encode())
            ca = x509.load_pem_x509_certificate((root/'ca.pem').read_bytes())
            assert x509.load_pem_x509_certificates(detail['chainPem'].encode()) == [ca]
            assert detail['status'] == 'VALID'
            cert.verify_directly_issued_by(ca)
            assert cert.public_key().public_numbers() == key.public_key().public_numbers()
            assert ExtendedKeyUsageOID.CLIENT_AUTH in cert.extensions.get_extension_for_class(x509.ExtendedKeyUsage).value
            fp = cert.fingerprint(hashes.SHA256()).hex()
            assert fp == detail['fingerprint'] == row['fingerprint']
            assert cert.public_bytes(serialization.Encoding.DER) == issued_der
            hit = call(reader, 'GET', base + '?fingerprint=' + fp)
            assert hit['total'] == 1 and [x['id'] for x in hit['items']] == [rid]
            assert hit['items'][0]['fingerprint'] == fp
            miss = call(reader, 'GET', base + '?fingerprint=' + '0'*64)
            assert miss['total'] == 0 and miss['items'] == []
        print(f'PASS create/idempotency/list/detail: source={label}, id={rid}, sha256={fp}', flush=True)
        device = session(root)
        (root/'device.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        (root/'device.key').write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        device.cert = (str(root/'device.pem'), str(root/'device.key'))
        call(device, 'POST', fm + '/api/auth/certificate-login', reject=(401,403))
        device.headers['Authorization'] = api.headers['Authorization']
        call(device, 'POST', fm + '/api/account/certificates', {'password':creds['password']})
        del device.headers['Authorization']
        token = call(device, 'POST', fm + '/api/auth/certificate-login')['token']
        device.headers['Authorization'] = 'Bearer ' + token
        assert call(device, 'GET', fm + '/api/auth/info')['username'] == 'admin'
        print('PASS programmatic mTLS: bound device -> certificate login -> authenticated /info', flush=True)
        other, base = routes[1-i]
        call(other, 'POST', base + '/' + str(rid) + '/revoke')
        started = time.monotonic()
        for reader, base in routes:
            assert call(reader, 'GET', base + '/' + str(rid))['status'].upper() == 'REVOKED'
        # FM proxy invalidates immediately. GM-origin changes may use a cached VALID for 30s.
        window = 32 if i == 1 else 2
        while True:
            code, _ = outcome(device, 'GET', fm + '/api/auth/info')
            elapsed = time.monotonic() - started
            assert elapsed <= window, ('old JWT not rejected in time', elapsed, code)
            if code in (401,403):
                break
            assert i == 1 and code == 200, ('unexpected old JWT result', code)
            time.sleep(min(.25, window-elapsed))
        del device.headers['Authorization']
        call(device, 'POST', fm + '/api/auth/certificate-login', reject=(401,403))
        print(f'Old JWT denial observed after {elapsed:.2f}s', flush=True)
        print('PASS cross-revocation: state, new login and issued device JWT', flush=True)
    print('NOT RUN browser/OS certificate picker (no certificate store modified)')
