(function (globalScope) {
    function extractLoginErrorMessage(error) {
        if (error?.response) {
            return error.response.data?.message || error.response.statusText || '登录失败';
        }
        if (error?.message) {
            return error.message;
        }
        if (error?.request) {
            return '网络错误，请检查服务器是否运行';
        }
        return '登录失败';
    }

    const exported = {
        extractLoginErrorMessage
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.LoginErrorHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
