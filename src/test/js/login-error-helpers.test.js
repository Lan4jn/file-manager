const test = require('node:test');
const assert = require('node:assert/strict');

const {
    extractLoginErrorMessage
} = require('../../main/resources/static/js/login-error-helpers.js');

test('should prefer interceptor error message for login failures', () => {
    assert.equal(
        extractLoginErrorMessage(new Error('用户名不存在或密码错误，剩余 4 次重试机会')),
        '用户名不存在或密码错误，剩余 4 次重试机会'
    );
});

test('should prefer response message when present', () => {
    assert.equal(
        extractLoginErrorMessage({
            response: {
                data: {
                    message: '登录挑战已失效，请刷新后重试'
                }
            }
        }),
        '登录挑战已失效，请刷新后重试'
    );
});

test('should return network error message for request failures', () => {
    assert.equal(
        extractLoginErrorMessage({ request: {} }),
        '网络错误，请检查服务器是否运行'
    );
});

test('should fall back to default message when no detail exists', () => {
    assert.equal(
        extractLoginErrorMessage({}),
        '登录失败'
    );
});
