const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..', '..');
const indexHtml = fs.readFileSync(path.join(projectRoot, 'src/main/resources/static/index.html'), 'utf8');
const pomXml = fs.readFileSync(path.join(projectRoot, 'pom.xml'), 'utf8');

test('should keep only the new message center implementation in index.html', () => {
    const legacyMessageCenterMatches = indexHtml.match(/<div class="message-center" v-if="isLoggedIn">/g) || [];
    const newMessageCenterMatches = indexHtml.match(/<div v-if="isLoggedIn" class="message-center-shell">/g) || [];
    const legacyForwardDialogMatches = indexHtml.match(/title="转发分享消息"/g) || [];
    const newForwardDialogMatches = indexHtml.match(/title="转发分享"/g) || [];

    assert.equal(legacyMessageCenterMatches.length, 0);
    assert.equal(newMessageCenterMatches.length, 1);
    assert.equal(legacyForwardDialogMatches.length, 0);
    assert.equal(newForwardDialogMatches.length, 1);
});

test('should set current release to 1.9-build145 and document the build', () => {
    const version = pomXml.match(/<artifactId>file-manager<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1];
    assert.equal(version, '1.9-build145');

    const versionNotePath = path.join(projectRoot, 'src/main/resources/version-notes/v1.9.md');
    assert.equal(fs.existsSync(versionNotePath), true);

    const versionNote = fs.readFileSync(versionNotePath, 'utf8');
    assert.match(versionNote, /# v1\.9 版本更新内容/);
    assert.match(versionNote, /## build145/);
    assert.match(versionNote, /## build144/);
    assert.match(versionNote, /## build143/);
    assert.match(versionNote, /## build142/);
    assert.match(versionNote, /## build141/);
});
