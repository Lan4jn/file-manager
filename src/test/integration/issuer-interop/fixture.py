"""Generate a per-run issuer, loopback TLS endpoint and JVM trust store."""
from datetime import datetime, timedelta, timezone
from ipaddress import ip_address
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization as ser
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID


def generate(root, password):
    now = datetime.now(timezone.utc)
    encryption = ser.BestAvailableEncryption(password.encode())

    def cert(name, key, signer, issuer=None):
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)])
        ca = issuer is None
        b = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject if ca else issuer.subject)
             .public_key(key.public_key()).serial_number(x509.random_serial_number())
             .not_valid_before(now-timedelta(minutes=1)).not_valid_after(now+timedelta(days=7))
             .add_extension(x509.BasicConstraints(ca, 0 if ca else None), critical=True)
             .add_extension(x509.KeyUsage(True,False,not ca,False,False,ca,ca,None,None), critical=True)
             .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False))
        if not ca:
            b = (b.add_extension(x509.SubjectAlternativeName([x509.IPAddress(ip_address('127.0.0.1'))]), critical=False)
                 .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False))
        return b.sign(signer, hashes.SHA256())

    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    ca = cert('issuer-interop disposable CA', ca_key, ca_key)
    (root/'ca.pem').write_bytes(ca.public_bytes(ser.Encoding.PEM))
    (root/'ca.p12').write_bytes(pkcs12.serialize_key_and_certificates(b'issuer',ca_key,ca,None,encryption))
    (root/'trust.p12').write_bytes(pkcs12.serialize_java_truststore([pkcs12.PKCS12Certificate(ca,b'issuer')], encryption))
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    server = cert('loopback fixture', key, ca_key, ca)
    (root/'server.p12').write_bytes(pkcs12.serialize_key_and_certificates(b'server',key,server,[ca],encryption))
