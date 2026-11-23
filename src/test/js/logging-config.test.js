const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..', '..');
const bundledLog4j = path.join(projectRoot, 'src/main/resources/log4j2.xml');
const externalLog4j = path.join(projectRoot, 'config/log4j2.xml');
const externalApplicationYml = fs.readFileSync(path.join(projectRoot, 'config/application.yml'), 'utf8');
const devApplicationYml = fs.readFileSync(path.join(projectRoot, 'config/application-dev.yml'), 'utf8');
const prodApplicationYml = fs.readFileSync(path.join(projectRoot, 'config/application-prod.yml'), 'utf8');

test('should bundle log4j2 configuration into jar resources', () => {
    assert.equal(fs.existsSync(bundledLog4j), true);
    assert.equal(fs.existsSync(externalLog4j), false);
    const log4jXml = fs.readFileSync(bundledLog4j, 'utf8');
    assert.match(log4jXml, /<RollingFile name="FileAppender"/);
    assert.match(log4jXml, /<Property name="LOG_DIR">\$\{sys:LOG_PATH:-logs\}<\/Property>/);
    assert.match(log4jXml, /fileName="\$\{LOG_DIR\}\/file-manager\.log"/);
});

test('should not require external log4j2 config path from application yaml', () => {
    assert.doesNotMatch(externalApplicationYml, /logging:\s*[\s\S]*config:\s*\.\/config\/log4j2\.xml/);
});

test('should configure logging package with current application package', () => {
    assert.doesNotMatch(devApplicationYml + prodApplicationYml, /com\.poss\.file/);
    assert.match(devApplicationYml + prodApplicationYml, /com\.lan4jn\.file/);
});
