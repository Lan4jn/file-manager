var createClientCertificateHelpers = function(api) {
    return {
        isEnabled: async function() {
            try {
                var res = await api.get('/auth/runtime-config');
                return res.data && res.data.clientCertificateEnabled;
            } catch (e) {
                return false;
            }
        },
        login: async function() {
            return await api.post('/auth/certificate-login');
        },
        getCertificates: async function() {
            return await api.get('/account/certificates');
        },
        bindCertificate: async function(password) {
            return await api.post('/account/certificates', { password: password });
        },
        revokeCertificate: async function(id) {
            return await api.delete('/account/certificates/' + id);
        }
    };
};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = createClientCertificateHelpers;
}
