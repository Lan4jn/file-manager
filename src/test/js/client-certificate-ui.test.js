const test = require('node:test');
const assert = require('node:assert/strict');
const create = require('../../main/resources/static/js/client-certificate-ui.js');
test('login carries no client-provided identity; management uses the account-scoped API',async()=>{
 const calls=[];
 const api=Object.fromEntries(['get','post','delete'].map(method=>[method,async(...args)=>{calls.push([method,...args]);return {code:200,data:[]};}]));
 const client=create(api); await client.login();await client.getCertificates();await client.bindCertificate('example');await client.revokeCertificate(12);
 assert.deepEqual(calls,[['post','/auth/certificate-login'],['get','/account/certificates'],['post','/account/certificates',{password:'example'}],['delete','/account/certificates/12']]);
});
