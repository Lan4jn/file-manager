const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const script = path.resolve(__dirname, '../../main/resources/static/js/standard-cert-ui.js');
const csrPem = '-----BEGIN CERTIFICATE REQUEST-----\nYWFh\n-----END CERTIFICATE REQUEST-----';
const ui = () => { assert.ok(fs.existsSync(script), '缺少标准客户端证书组件'); return require(script); };
test('只接受单个 CSR，不接受附加私钥或证书', () => {
    for (const invalid of ['', csrPem.replaceAll('CERTIFICATE REQUEST','NEW CERTIFICATE REQUEST'), csrPem + '\n-----BEGIN PRIVATE KEY-----', csrPem + '\n' + csrPem, csrPem.replaceAll('REQUEST', 'KEY')]) assert.equal(ui().validCsr(invalid), false);
    assert.equal(ui().validCsr(csrPem), true);
});
test('相同签发在断线后复用请求编号，并拒绝进行中的重复提交', async () => {
    let counter = 0; const issue = ui().createIssuer(() => 'id-' + ++counter);
    let rejectRequest;
    const pending = issue(csrPem, '设备', () => new Promise((_, reject) => {rejectRequest = reject;}));
    await assert.rejects(issue(csrPem, '设备', () => assert.fail()), /正在/);
    rejectRequest(new Error('offline')); await assert.rejects(pending);
    let payload; await issue(csrPem, '设备', body => {payload = body;});
    assert.deepEqual(payload, {requestId:'id-1', csrPem, deviceLabel:'设备'});
});
test('撤销状态优先于时间，过期证书不能作为有效证书', () => {
    assert.equal(ui().statusOf({status:'VALID',expiresAt:1}), 'EXPIRED');
    assert.equal(ui().statusOf({status:'REVOKED',expiresAt:1}), 'REVOKED');
});
function mountMethods(api, enabled=true) {
    const component = ui().component;
    return Object.assign(component.data(), component.methods, {api, enabled});
}
test('按开关停止访问，按指纹和零起始页码读取 Result 内记录', async () => {
    const calls = [];
    const state = mountMethods({get:async (...args) => {calls.push(args);return {code:200,data:{items:[{id:'r'}],total:51}};}},false);
    await state.list(); assert.equal(calls.length,0);
    state.enabled=true; state.filter='aabb'; await state.list(2);
    assert.deepEqual(calls,[['/admin/standard-client-certificates',{params:{fingerprint:'aabb',page:1,size:50}}]]);
    assert.deepEqual(state.items,[{id:'r'}]); assert.equal(state.total,51);
});
test('取消吊销不发送请求，确认后展示返回的吊销记录', async () => {
    const row={id:'x/y',deviceLabel:'设备',status:'VALID'}, revoked={...row,status:'REVOKED'};
    const paths=[]; const state=mountMethods({post:async p => {paths.push(p);return {data:revoked};},get:async () => ({data:{items:[revoked],total:1}})});
    state.$confirm=async () => {throw 'cancel';}; await state.revoke(row); assert.deepEqual(paths, []);
    state.$confirm=async text => assert.match(text,/不会解除.*绑定/); await state.revoke(row);
    assert.deepEqual(paths,['/admin/standard-client-certificates/x%2Fy/revoke']); assert.deepEqual(state.detail,revoked);
});
