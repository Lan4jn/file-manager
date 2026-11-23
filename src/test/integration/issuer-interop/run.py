"""Boot disposable GM and FM processes; completely ignore ambient app settings."""
import argparse
import base64
import os
import secrets
import shutil
import socket
import sqlite3
import subprocess
import tempfile
import time
from contextlib import contextmanager, closing
from pathlib import Path
import requests
from exercise import session, call, gm_login, verify
from fixture import generate


def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


@contextmanager
def service(root, name, java, jar, port_number, config, jvm=()):
    env = {k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','PATH','TEMP','TMP','LANG','HOME','USERPROFILE'}}
    env.update(config, SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(port_number))
    work = root/name
    work.mkdir(exist_ok=True)
    origin = f'https://127.0.0.1:{port_number}'
    with (work/'console.log').open('ab') as log:
        p = subprocess.Popen([str(java), *jvm, '-jar',str(jar)], env=env,cwd=work,stdout=log,stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic()+100
            while time.monotonic() < deadline and p.poll() is None:
                try:
                    session(root).get(origin+'/',timeout=2,allow_redirects=False)
                    break
                except requests.ConnectionError:
                    time.sleep(.3)
            else:
                # Do not dump unredacted application logs; they may contain credentials.
                raise RuntimeError(f'{name}: HTTPS readiness failed (exit={p.poll()})')
            yield origin
        finally:
            if p.poll() is None:
                p.terminate()
                try:
                    p.wait(10)
                except subprocess.TimeoutExpired:
                    p.kill()
                    p.wait()


def run(a, root):
    pw, admin, token = [secrets.token_urlsafe(32) for _ in range(3)]
    generate(root,pw)
    if a.fixture_only:
        print('PASS generated disposable CA, server key store and JVM trust store (no backend started)')
        return
    for kind in ('gm_jar', 'fm_jar'):
        setattr(a, kind, shutil.copyfile(getattr(a,kind), root/(kind+'.jar')))
    tls = dict(SERVER_SSL_ENABLED='true', SERVER_SSL_KEY_STORE=(root/'server.p12').as_uri(),
               SERVER_SSL_KEY_STORE_TYPE='PKCS12',SERVER_SSL_KEY_STORE_PASSWORD=pw,SERVER_SSL_KEY_ALIAS='server')
    gc = dict(tls, SPRING_DATASOURCE_URL='jdbc:h2:file:'+ (root/'gmdb').as_posix()+';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0',
              GMSSL_VAULT_DIR=str(root/'vault'),GMSSL_VAULT_MASTER_KEY=base64.b64encode(os.urandom(32)).decode(),
              GMSSL_ENGINE_TOKEN=secrets.token_urlsafe(32), GMSSL_BOOTSTRAP_USERNAME='interop', GMSSL_BOOTSTRAP_PASSWORD=admin,
              GMSSL_STANDARD_CA_PKCS12=str(root/'ca.p12'),GMSSL_STANDARD_CA_ALIAS='issuer',GMSSL_STANDARD_CA_PASSWORD=pw)
    gp = port()
    def gm():
        return service(root,'gm',a.gm_java,a.gm_jar,gp,gc)
    with gm() as origin:
        pid = call(gm_login(root,origin,admin),'POST',origin+'/api/v1/projects',{'name':'interop'})['id']
    gc.update(GMSSL_STANDARD_INTEGRATION_ENABLED='true',GMSSL_STANDARD_INTEGRATION_PROJECT_ID=str(pid),GMSSL_STANDARD_INTEGRATION_TOKEN=token)
    with gm() as origin:
        fc = dict(tls,SERVER_SSL_CLIENT_AUTH='want',SERVER_SSL_TRUST_STORE=(root/'trust.p12').as_uri(),
                  SERVER_SSL_TRUST_STORE_PASSWORD=pw,SERVER_SSL_TRUST_STORE_TYPE='PKCS12',APP_SECURITY_HTTP_PORT='-1',
                  APP_SECURITY_CLIENT_CERTIFICATE_ENABLED='true',APP_DATASOURCE_MIRROR_ENABLED='false',
                  SPRING_DATASOURCE_URL='jdbc:sqlite:'+(root/'users.db').as_posix(),SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.sqlite.JDBC',
                  SPRING_JPA_DATABASE_PLATFORM='org.hibernate.community.dialect.SQLiteDialect',SPRING_JPA_HIBERNATE_DDL_AUTO='update',
                  JWT_SECRET=secrets.token_urlsafe(64), JWT_EXPIRATION='600000', MINIO_ENDPOINT='http://127.0.0.1:1',
                  MINIO_ACCESS_KEY='fixtureonly',MINIO_SECRET_KEY=secrets.token_urlsafe(32),MINIO_DEFAULT_BUCKET='fixture',
                  ISSUERBRIDGE_ENABLED='true',ISSUERBRIDGE_BASEURL=origin,ISSUERBRIDGE_PROJECTID=str(pid),
                  ISSUERBRIDGE_APIKEY=token,ISSUERBRIDGE_ISSUERCAFILE=str(root/'ca.pem'))
        with service(root,'fm',a.fm_java,a.fm_jar,port(),fc,[f'-Djavax.net.ssl.trustStore={root / "trust.p12"}',
                     f'-Djavax.net.ssl.trustStorePassword={pw}','-Djavax.net.ssl.trustStoreType=PKCS12']) as fm:
            # HTTPS starts before the CommandLineRunner finishes populating the fresh DB.
            with closing(sqlite3.connect((root/'users.db').as_uri()+'?mode=ro', uri=True)) as db:
                start = time.monotonic()
                while not db.execute("select 1 from sys_user where username='admin'").fetchone():
                    if time.monotonic()-start > 15:
                        raise RuntimeError('FM fixture administrator not initialized')
                    time.sleep(.05)
                print(f'FM administrator fixture ready after {time.monotonic()-start:.2f}s', flush=True)
            verify(root,origin,fm,pid,token,admin)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--fixture-only',action='store_true')
    for flag in ['gm-java','gm-jar','fm-java','fm-jar']:
        p.add_argument('--'+flag,type=lambda x:Path(x).resolve(strict=True))
    a = p.parse_args()
    if not __debug__:
        p.error('do not disable Python assertions')
    if not a.fixture_only and not all((a.gm_java,a.gm_jar,a.fm_java,a.fm_jar)):
        p.error('supply both jars and Java executables')
    with tempfile.TemporaryDirectory(prefix='issuer-interop-') as folder:
        run(a, Path(folder))
