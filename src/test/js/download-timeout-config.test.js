const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..', '..');

function readConfig(relativePath) {
    return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8');
}

test('should define shared download timeout settings in application.yml', () => {
    const yaml = readConfig('config/application.yml');

    assert.match(yaml, /server:\s*[\s\S]*connection-timeout:/);
    assert.match(yaml, /minio:\s*[\s\S]*http:\s*[\s\S]*connect-timeout-ms:/);
    assert.match(yaml, /minio:\s*[\s\S]*http:\s*[\s\S]*read-timeout-ms:/);
    assert.match(yaml, /minio:\s*[\s\S]*http:\s*[\s\S]*write-timeout-ms:/);
});

test('should define environment overrides for download timeout settings', () => {
    const devYaml = readConfig('config/application-dev.yml');
    const prodYaml = readConfig('config/application-prod.yml');

    assert.match(devYaml, /minio:\s*[\s\S]*http:\s*[\s\S]*connect-timeout-ms:/);
    assert.match(prodYaml, /minio:\s*[\s\S]*http:\s*[\s\S]*connect-timeout-ms:/);
});
