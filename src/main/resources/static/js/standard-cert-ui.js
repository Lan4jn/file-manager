const StandardCertUi = (() => {
    const base = '/admin/standard-client-certificates'; // api.baseURL includes /api
    function validCsr(value) {
        return /^-----BEGIN CERTIFICATE REQUEST-----\r?\n(?:[A-Za-z0-9+/=]+\r?\n)+-----END CERTIFICATE REQUEST-----$/.test(value.trim());
    }
    function createIssuer(newId) {
        let pending, busy = false;
        return async (csr, label, post) => {
            if (busy) throw new Error('正在签发');
            const csrPem = csr.trim(), deviceLabel = label.trim();
            if (!validCsr(csrPem) || /PRIVATE KEY/i.test(deviceLabel)) throw new Error('只接受单份 PEM 格式的 CSR，请勿提交私钥');
            if (!deviceLabel) throw new Error('请输入设备名称');
            if (!pending || pending.csrPem !== csrPem || pending.deviceLabel !== deviceLabel) pending = {requestId: newId(), csrPem, deviceLabel};
            busy = true;
            try { const result = await post(pending); pending = null; return result; }
            finally { busy = false; }
        };
    }
    const statusOf = row => row.status === 'VALID' && Number(row.expiresAt) <= Date.now() ? 'EXPIRED' : row.status;
    const component = {
        props: {api: Function, enabled: Boolean, copyText: Function},
        data() {
            return {open:false, csr:'', label:'', fingerprint:'', filter:'', page:1, total:0, items:[], detail:null,
                busy:false, loading:false, error:'', seq:0, issueRequest:createIssuer(() => crypto.randomUUID ? crypto.randomUUID() : Array.from(crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2,'0')).join(''))};
        },
        watch: { enabled(value) { if (this.open && value) this.list(); } },
        methods: {
            statusOf, statusLabel(row) { return {VALID:'有效',REVOKED:'已吊销',EXPIRED:'已过期'}[statusOf(row)] || '未知'; },
            report(e) { this.error = e.response?.status === 503 ? '签发服务暂不可用，请稍后重试。' : e.response?.data?.message || e.message || '操作失败'; },
            async list(page=this.page) {
                this.page = page; this.error=''; this.detail=null;
                if (!this.enabled) return;
                const seq = ++this.seq; this.loading=true; this.items=[];
                try {
                    const {data} = await this.api.get(base, {params:{fingerprint:this.filter,page:page-1,size:50}});
                    if (seq === this.seq) { this.items=data.items; this.total=data.total; }
                } catch(e) { if (seq === this.seq) this.report(e); }
                finally { if (seq === this.seq) this.loading=false; }
            },
            async action(fn) {
                if (this.busy || !this.enabled) return;
                this.busy=true; this.error='';
                try { await fn(); } catch(e) { if (e !== 'cancel' && e !== 'close') this.report(e); }
                finally { this.busy=false; }
            },
            issue() { return this.action(async () => {
                const {data} = await this.issueRequest(this.csr,this.label, payload => this.api.post(base,payload));
                this.csr=''; this.label=''; this.filter=''; this.fingerprint=''; await this.list(1); this.detail=data;
            }); },
            inspect(row) { return this.action(async () => { this.detail=null; this.detail=(await this.api.get(base+'/'+encodeURIComponent(row.id))).data; }); },
            revoke(row) { return this.action(async () => {
                await this.$confirm('确认吊销设备「'+row.deviceLabel+'」的证书？吊销不会解除文件管理器的本地账户绑定。', '吊销证书', {type:'warning', confirmButtonText:'确认吊销', cancelButtonText:'取消'});
                const {data} = await this.api.post(base+'/'+encodeURIComponent(row.id)+'/revoke');
                await this.list(); this.detail=data;
            }); },
            async copy(value) { try { await this.copyText(value); this.$message.success('已复制'); } catch(e) { this.report(e); } },
            download(value,name) {
                const url=URL.createObjectURL(new Blob([value],{type:'application/x-pem-file'}));
                const link=document.createElement('a'); link.href=url; link.download=name+'.pem'; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
            }
        },
        template: `<section><el-button @click="open=true">管理标准客户端证书</el-button>
          <el-dialog v-model="open" title="标准客户端证书" width="min(900px, 96vw)" @open="list(1)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
            <el-alert v-if="!enabled" title="签发服务未启用或不可用" type="info" :closable="false" />
            <template v-else><p>在设备上生成 RSA 3072 位密钥，使用 SHA-256 签署 CSR。只粘贴 CERTIFICATE REQUEST，私钥留在设备上。</p>
            <el-form label-position="top" @submit.prevent="issue"><el-form-item label="设备名称"><el-input v-model="label" :disabled="busy" /></el-form-item>
            <el-form-item label="证书签名请求（PEM）"><el-input type="textarea" :rows="4" v-model="csr" :disabled="busy" /></el-form-item>
            <el-button type="primary" native-type="submit" :loading="busy" :disabled="!csr.trim() || !label.trim() || loading">签发证书</el-button></el-form>
            <h3>已签发证书</h3><el-form inline @submit.prevent="filter=fingerprint.trim();list(1)"><el-form-item label="指纹"><el-input clearable v-model="fingerprint" placeholder="SHA-256 指纹" /></el-form-item><el-button native-type="submit" :disabled="busy || loading">查询</el-button></el-form>
            <el-table :data="items" v-loading="loading" empty-text="没有匹配的证书"><el-table-column prop="deviceLabel" label="设备"/><el-table-column label="状态"><template #default="{row}">{{ statusLabel(row) }}</template></el-table-column><el-table-column label="到期时间" min-width="150"><template #default="{row}">{{ new Date(row.expiresAt).toLocaleString() }}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button :disabled="busy" @click="inspect(row)">详情</el-button></template></el-table-column></el-table>
            <el-pagination v-if="total > 50" layout="prev, pager, next, total" :page-size="50" :total="total" :current-page="page" :disabled="busy || loading" @current-change="list" />
            <section v-if="detail" aria-label="证书详情"><h3>{{ detail.deviceLabel }} · {{ statusLabel(detail) }}</h3><p>序列号：{{ detail.serial }}</p><p style="overflow-wrap:anywhere">SHA-256：{{ detail.fingerprint }}</p><p v-if="typeof detail.bound === 'boolean'">本地账户：{{ detail.bound ? '已绑定' : '未绑定' }}</p>
            <el-button @click="copy(detail.fingerprint)">复制指纹</el-button><el-button :disabled="!detail.certificatePem" @click="copy(detail.certificatePem)">复制证书</el-button><el-button :disabled="!detail.certificatePem" @click="download(detail.certificatePem,'client-cert')">下载证书</el-button><el-button :disabled="!detail.chainPem" @click="download(detail.chainPem,'client-chain')">下载证书链</el-button>
            <p>吊销证书不会解除本地账户绑定；如需解绑，请到客户端证书管理中操作。</p><el-button type="danger" plain :disabled="busy || statusOf(detail) !== 'VALID'" @click="revoke(detail)">吊销证书</el-button></section></template>
            <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
          </el-dialog></section>`
    };
    return {validCsr, createIssuer, statusOf, component};
})();
if (typeof module !== 'undefined' && module.exports) module.exports = StandardCertUi;
