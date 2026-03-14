const { createApp, ref, computed, onMounted, watch } = Vue;

const app = createApp({
    setup() {
        // State
        const isLoggedIn = ref(false);
        const token = ref(localStorage.getItem('token') || '');
        const userInfo = ref({});
        const currentMenu = ref('buckets');
        const loginForm = ref({ username: '', password: '' });
        const loginLoading = ref(false);

        // Bucket management
        const bucketList = ref([]);
        const bucketLoading = ref(false);
        const currentBucket = ref('');
        const showBucketDialog = ref(false);
        const newBucketName = ref('');
        const bucketSortBy = ref('name');
        const bucketOrder = ref('asc');

        // File management
        const fileList = ref([]);
        const fileLoading = ref(false);
        const currentFolder = ref('');
        const breadcrumb = ref([]);
        const selectedFiles = ref([]);
        const fileSortBy = ref('name');
        const fileOrder = ref('asc');
        // 分页相关
        const filePage = ref(1);
        const filePageSize = ref(10);
        const fileTotal = ref(0);

        // Dialogs
        const showUploadDialog = ref(false);
        const uploadRef = ref(null);
        const uploading = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);
        const showUserDialog = ref(false);
        const showRoleDialog = ref(false);
        const showMinioKeyDialog = ref(false);
        const showHelpDialog = ref(false);
        const showVersionDialog = ref(false);
        const helpContent = ref('');
        const versionList = ref([]);
        const selectedVersion = ref('');
        const versionContent = ref('');

        // Form data
        const newFolderName = ref('');
        const createTestFile = ref(false);
        const shareForm = ref({ objectName: '', expirationHours: 24, password: '' });
        const userForm = ref({});
        const roleForm = ref({});
        const minioKeyForm = ref({
            name: '',
            bucketPermissions: [],  // 改为数组，每个元素包含 bucket, canRead, canWrite, canDelete
            expireDays: 0
        });

        // 辅助函数：添加桶权限
        const addBucketPermission = () => {
            minioKeyForm.value.bucketPermissions.push({
                bucket: '',
                canRead: true,
                canWrite: false,
                canDelete: false
            });
        };

        // 辅助函数：移除桶权限
        const removeBucketPermission = (index) => {
            minioKeyForm.value.bucketPermissions.splice(index, 1);
        };

        // Lists
        const shareList = ref([]);
        const userList = ref([]);
        const roleList = ref([]);
        const minioKeyList = ref([]);
        const minioKeyLoading = ref(false);

        // Loading states
        const shareLoading = ref(false);
        const userLoading = ref(false);
        const roleLoading = ref(false);

        // File config
        const fileConfig = ref({
            allowedExtensionsText: '',
            maxFileSizeMB: 100
        });

        // Share config
        const shareConfig = ref({
            batchShareMaxSizeGB: 2,
            batchDownloadMaxSizeGB: 2,
            expireHours: 168
        });

        // API
        const api = axios.create({
            baseURL: '/api',
            timeout: 30000
        });

        api.interceptors.request.use(config => {
            if (token.value) {
                config.headers.Authorization = 'Bearer ' + token.value;
            }
            return config;
        });

        api.interceptors.response.use(
            response => {
                // 对 blob 响应（文件下载）不做处理
                if (response.config.responseType === 'blob') {
                    return response;
                }
                // 检查业务状态码
                if (response.data && response.data.code !== 200) {
                    return Promise.reject(new Error(response.data.message || '请求失败'));
                }
                return response.data;
            },
            error => {
                if (error.response && error.response.status === 401) {
                    logout();
                }
                return Promise.reject(error);
            }
        );

        // Computed
        const isAdmin = computed(() => {
            return userInfo.value.roles && userInfo.value.roles.includes('ADMIN');
        });

        const uploadUrl = computed(() => '/api/file/upload');
        const uploadHeaders = computed(() => ({
            Authorization: 'Bearer ' + token.value
        }));
        const uploadData = computed(() => ({
            folder: currentFolder.value,
            bucket: currentBucket.value
        }));

        // SHA512 hash function - 使用纯 JS 实现，兼容所有环境
        const sha512 = (message) => {
            // 统一使用纯 JS 实现，避免 crypto.subtle 在非安全上下文的问题
            const hexChars = '0123456789abcdef';
            const msgBytes = new Uint8Array(message.length);
            for (let i = 0; i < message.length; i++) {
                msgBytes[i] = message.charCodeAt(i);
            }

            // SHA-512 参数
            let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
            let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;
            const k = [
                0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
            ];

            // 填充消息
            const msgBitLen = msgBytes.length * 8;
            const padLen = msgBytes.length % 128 < 112 ? 112 - msgBytes.length % 128 : 240 - msgBytes.length % 128;
            const padded = new Uint8Array(msgBytes.length + padLen + 8);
            padded.set(msgBytes, 0);
            padded[msgBytes.length] = 0x80;

            // 大端序写入消息长度
            const view = new DataView(padded.buffer);
            view.setUint32(padded.length - 4, msgBitLen, false);

            // 辅助函数
            const rotr = (x, n) => (x >>> n) | (x << (64 - n));
            const sum0 = x => rotr(x, 28) ^ rotr(x, 34) ^ rotr(x, 39);
            const sum1 = x => rotr(x, 14) ^ rotr(x, 18) ^ rotr(x, 41);
            const sigma0 = x => rotr(x, 1) ^ rotr(x, 8) ^ (x >>> 7);
            const sigma1 = x => rotr(x, 19) ^ rotr(x, 61) ^ (x >>> 6);

            // 处理每个块
            for (let i = 0; i < padded.length; i += 128) {
                const w = new Uint32Array(80);
                for (let j = 0; j < 16; j++) {
                    w[j] = view.getUint32(i + j * 4, false);
                }
                for (let j = 16; j < 80; j++) {
                    w[j] = (sigma1(w[j - 2]) + w[j - 7] + sigma0(w[j - 15]) + w[j - 16]) >>> 0;
                }

                let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, hh = h7;
                for (let j = 0; j < 80; j++) {
                    const t1 = (hh + sum1(e) + ((e & f) ^ (~e & g)) + k[j] + w[j]) >>> 0;
                    const t2 = (sum0(a) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
                    hh = g;
                    g = f;
                    f = e;
                    e = (d + t1) >>> 0;
                    d = c;
                    c = b;
                    b = a;
                    a = (t1 + t2) >>> 0;
                }

                h0 = (h0 + a) >>> 0;
                h1 = (h1 + b) >>> 0;
                h2 = (h2 + c) >>> 0;
                h3 = (h3 + d) >>> 0;
                h4 = (h4 + e) >>> 0;
                h5 = (h5 + f) >>> 0;
                h6 = (h6 + g) >>> 0;
                h7 = (h7 + hh) >>> 0;
            }

            // 输出十六进制
            const result = [];
            [h0, h1, h2, h3, h4, h5, h6, h7].forEach(h => {
                for (let i = 3; i >= 0; i--) {
                    result.push(hexChars[(h >>> (i * 4 + 4)) & 0xf]);
                    result.push(hexChars[(h >>> (i * 4)) & 0xf]);
                }
            });
            return result.join('');
        };

        // Methods
        const handleLogin = async () => {
            console.log('[Login] Starting login attempt');
            if (!loginForm.value.username || !loginForm.value.password) {
                ElementPlus.ElMessage.warning('请输入用户名和密码');
                return;
            }
            loginLoading.value = true;
            try {
                console.log('[Login] Sending login request to /auth/login');
                // 使用 base64 编码密码传输
                const encodedPassword = btoa(unescape(encodeURIComponent(loginForm.value.password)));
                const res = await api.post('/auth/login', {
                    username: loginForm.value.username,
                    password: encodedPassword
                });
                console.log('[Login] Response received:', res);
                if (res.code === 200) {
                    token.value = res.data.token;
                    localStorage.setItem('token', token.value);
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    loadBuckets();
                } else {
                    ElementPlus.ElMessage.error(res.message || '登录失败');
                }
            } catch (error) {
                console.error('[Login] Error:', error);
                let errorMsg = '登录失败';
                if (error.response) {
                    errorMsg = error.response.data?.message || error.response.statusText || errorMsg;
                } else if (error.request) {
                    errorMsg = '网络错误，请检查服务器是否运行';
                }
                ElementPlus.ElMessage.error(errorMsg);
            } finally {
                loginLoading.value = false;
            }
        };

        const logout = () => {
            token.value = '';
            localStorage.removeItem('token');
            userInfo.value = {};
            isLoggedIn.value = false;
            loginForm.value = { username: '', password: '' };
        };

        const handleCommand = (command) => {
            if (command === 'logout') {
                logout();
            } else if (command === 'profile') {
                // Show profile dialog
            }
        };

        const checkAuth = async () => {
            if (!token.value) return;
            try {
                const res = await api.get('/auth/info');
                if (res.code === 200) {
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    loadBuckets();
                } else {
                    logout();
                }
            } catch (error) {
                logout();
            }
        };

        const handleMenuSelect = (index) => {
            currentMenu.value = index;
            if (index === 'buckets') {
                loadBuckets();
            } else if (index === 'files') {
                if (!currentBucket.value && bucketList.value.length > 0) {
                    currentBucket.value = bucketList.value[0].name;
                }
                loadFiles();
            } else if (index === 'shares') {
                loadShares();
            } else if (index === 'users') {
                loadUsers();
            } else if (index === 'roles') {
                loadRoles();
            } else if (index === 'config') {
                loadFileConfig();
                loadShareConfig();
            } else if (index === 'minio-keys') {
                loadMinioKeys();
            }
        };

        // Bucket management
        const loadBuckets = async () => {
            bucketLoading.value = true;
            try {
                const res = await api.get('/file/buckets', {
                    params: { sortBy: bucketSortBy.value, order: bucketOrder.value }
                });
                if (res.code === 200) {
                    bucketList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载存储桶列表失败');
            } finally {
                bucketLoading.value = false;
            }
        };

        const handleBucketSortChange = ({ prop, order }) => {
            if (!prop) return;
            if (prop === 'creationDate') {
                bucketSortBy.value = 'time';
            } else if (prop === 'objectCount') {
                bucketSortBy.value = 'size';
            } else {
                bucketSortBy.value = prop;
            }
            bucketOrder.value = order === 'ascending' ? 'asc' : 'desc';
            loadBuckets();
        };

        const selectBucket = (bucketName) => {
            currentBucket.value = bucketName;
            currentMenu.value = 'files';
            loadFiles();
        };

        const createBucket = async () => {
            if (!newBucketName.value) {
                ElementPlus.ElMessage.warning('请输入存储桶名称');
                return;
            }
            try {
                const res = await api.post('/file/buckets', { bucketName: newBucketName.value });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('创建成功');
                    showBucketDialog.value = false;
                    newBucketName.value = '';
                    loadBuckets();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const deleteBucket = async (bucket) => {
            try {
                await ElementPlus.ElMessageBox.confirm(
                    '确定要删除存储桶 "' + bucket.name + '" 吗？删除后其中的所有文件将被永久删除！',
                    '警告',
                    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
                );
                const res = await api.delete('/file/buckets', { params: { bucketName: bucket.name } });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadBuckets();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败: ' + (error.response?.data?.message || error.message));
                }
            }
        };

        // File management
        const loadFiles = async () => {
            if (!currentBucket.value) return;
            fileLoading.value = true;
            try {
                const res = await api.get('/file/list', {
                    params: {
                        folder: currentFolder.value,
                        bucket: currentBucket.value,
                        sortBy: fileSortBy.value,
                        order: fileOrder.value,
                        page: filePage.value,
                        size: filePageSize.value
                    }
                });
                if (res.code === 200) {
                    // 判断返回的是分页结果还是普通列表
                    if (res.data.items !== undefined) {
                        // 分页模式
                        fileList.value = res.data.items || [];
                        fileTotal.value = res.data.total || 0;
                    } else {
                        // 非分页模式（兼容旧版）
                        fileList.value = res.data || [];
                        fileTotal.value = res.data ? res.data.length : 0;
                    }
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载文件列表失败');
            } finally {
                fileLoading.value = false;
            }
        };

        // 分页大小改变
        const handleFileSizeChange = (val) => {
            filePageSize.value = val;
            filePage.value = 1; // 重置到第一页
            loadFiles();
        };

        // 页码改变
        const handleFilePageChange = (val) => {
            filePage.value = val;
            loadFiles();
        };

        const handleFileSortChange = ({ prop, order }) => {
            if (!prop) return;
            if (prop === 'fileName') {
                fileSortBy.value = 'name';
            } else if (prop === 'uploadTime') {
                fileSortBy.value = 'time';
            } else {
                fileSortBy.value = prop;
            }
            fileOrder.value = order === 'ascending' ? 'asc' : 'desc';
            loadFiles();
        };

        const refreshFiles = () => {
            loadFiles();
        };

        // 打开上传对话框时加载配置
        const openUploadDialog = () => {
            loadFileConfig();
            showUploadDialog.value = true;
        };

        const navigateToFolder = (path) => {
            currentFolder.value = path || '';
            updateBreadcrumb();
            loadFiles();
        };

        const updateBreadcrumb = () => {
            if (!currentFolder.value) {
                breadcrumb.value = [];
                return;
            }
            const parts = currentFolder.value.split('/').filter(p => p);
            const crumbs = [];
            let path = '';
            for (const part of parts) {
                path += part + '/';
                crumbs.push({ name: part, path: path });
            }
            breadcrumb.value = crumbs;
        };

        const downloadFile = async (file) => {
            // 目录下载
            if (file.isFolder) {
                try {
                    ElementPlus.ElMessage.info('正在打包目录，请稍候...');
                    const response = await api.get('/file/download-folder', {
                        params: { objectName: file.objectName, bucket: currentBucket.value },
                        responseType: 'blob',
                        timeout: 300000 // 5分钟超时
                    });
                    const url = window.URL.createObjectURL(new Blob([response.data]));
                    const link = document.createElement('a');
                    link.href = url;
                    link.setAttribute('download', file.fileName + '.zip');
                    document.body.appendChild(link);
                    link.click();
                    link.remove();
                    window.URL.revokeObjectURL(url);
                    ElementPlus.ElMessage.success('下载完成');
                } catch (error) {
                    if (error.response?.data?.message) {
                        ElementPlus.ElMessage.error(error.response.data.message);
                    } else {
                        ElementPlus.ElMessage.error('下载失败: ' + (error.message || ''));
                    }
                }
                return;
            }

            // 文件下载
            try {
                const response = await api.get('/file/download', {
                    params: { objectName: file.objectName, bucket: currentBucket.value },
                    responseType: 'blob'
                });
                const url = window.URL.createObjectURL(new Blob([response.data]));
                const link = document.createElement('a');
                link.href = url;
                link.setAttribute('download', file.fileName);
                document.body.appendChild(link);
                link.click();
                link.remove();
                window.URL.revokeObjectURL(url);
            } catch (error) {
                ElementPlus.ElMessage.error('下载失败');
            }
        };

        const previewFile = (file) => {
            const params = new URLSearchParams();
            params.append('objectName', file.objectName);
            params.append('bucket', currentBucket.value);
            window.open('/api/file/preview?' + params.toString());
        };

        const deleteFile = async (file) => {
            try {
                await ElementPlus.ElMessageBox.confirm(
                    '确定要删除 ' + (file.isFolder ? '文件夹' : '文件') + ' "' + file.fileName + '" 吗？',
                    '提示',
                    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
                );
                const res = await api.delete('/file/delete', {
                    params: { objectName: file.objectName, bucket: currentBucket.value }
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadFiles();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败');
                }
            }
        };

        // Batch share
        const batchShare = async () => {
            if (selectedFiles.value.length === 0) {
                ElementPlus.ElMessage.warning('请先选择要分享的文件');
                return;
            }
            try {
                const objectNames = selectedFiles.value.map(f => f.objectName);
                const res = await api.post('/share/batch-create', {
                    objectNames: objectNames,
                    bucket: currentBucket.value,
                    expirationHours: 24
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('分享创建成功（仅分享当前选择的内容）');
                    // Copy share link
                    const link = window.location.origin + '/share/' + res.data.shareCode;
                    navigator.clipboard.writeText(link);
                    ElementPlus.ElMessage.info('分享链接已复制到剪贴板');
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建分享失败: ' + (error.response?.data?.message || error.message));
            }
        };

        // Batch download
        const batchDownload = async () => {
            if (selectedFiles.value.length === 0) {
                ElementPlus.ElMessage.warning('请先选择要下载的文件');
                return;
            }
            try {
                ElementPlus.ElMessage.info('正在打包文件，请稍候...');
                const objectNames = selectedFiles.value.map(f => f.objectName).join(',');
                const response = await api.get('/file/batch-download', {
                    params: { objectNames: objectNames, bucket: currentBucket.value },
                    responseType: 'blob',
                    timeout: 600000 // 10分钟超时
                });
                const url = window.URL.createObjectURL(new Blob([response.data]));
                const link = document.createElement('a');
                link.href = url;
                link.setAttribute('download', '批量下载_' + selectedFiles.value.length + '个文件.zip');
                document.body.appendChild(link);
                link.click();
                link.remove();
                window.URL.revokeObjectURL(url);
                ElementPlus.ElMessage.success('下载完成');
            } catch (error) {
                if (error.response?.data?.message) {
                    ElementPlus.ElMessage.error(error.response.data.message);
                } else {
                    ElementPlus.ElMessage.error('下载失败: ' + (error.message || ''));
                }
            }
        };

        // Batch delete
        const batchDelete = async () => {
            if (selectedFiles.value.length === 0) {
                ElementPlus.ElMessage.warning('请先选择要删除的文件');
                return;
            }
            try {
                await ElementPlus.ElMessageBox.confirm(
                    '确定要删除选中的 ' + selectedFiles.value.length + ' 个文件/文件夹吗？此操作不可恢复！',
                    '确认删除',
                    {
                        confirmButtonText: '确定',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }
                );
                const objectNames = selectedFiles.value.map(f => f.objectName).join(',');
                await api.delete('/file/batch-delete', {
                    params: { objectNames: objectNames, bucket: currentBucket.value }
                });
                ElementPlus.ElMessage.success('删除成功');
                loadFiles();
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败: ' + (error.response?.data?.message || error.message));
                }
            }
        };

        const createFolder = async () => {
            if (!newFolderName.value) {
                ElementPlus.ElMessage.warning('请输入文件夹名称');
                return;
            }
            try {
                const path = currentFolder.value + newFolderName.value;
                const res = await api.post('/file/folder', null, {
                    params: { path: path, bucket: currentBucket.value, createTestFile: createTestFile.value }
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('创建成功');
                    showFolderDialog.value = false;
                    newFolderName.value = '';
                    createTestFile.value = false;
                    loadFiles();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建失败');
            }
        };

        const shareFile = (file) => {
            shareForm.value = {
                objectName: file.objectName,
                bucket: currentBucket.value,
                expirationHours: 24,
                password: '',
                isFolder: file.isFolder || false
            };
            showShareDialog.value = true;
        };

        const createShare = async () => {
            try {
                const res = await api.post('/share/create', {
                    objectName: shareForm.value.objectName,
                    bucket: shareForm.value.bucket,
                    expirationHours: shareForm.value.expirationHours,
                    password: shareForm.value.password
                });
                if (res.code === 200) {
                    let successMsg = '分享创建成功';
                    if (shareForm.value.isFolder) {
                        successMsg += '（仅分享当前目录的文件状态）';
                    }
                    ElementPlus.ElMessage.success(successMsg);
                    showShareDialog.value = false;
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建分享失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const handleSelectionChange = (selection) => {
            selectedFiles.value = selection;
        };

        const submitUpload = () => {
            if (uploadRef.value) {
                uploading.value = true;
                console.log('Submitting upload, ref:', uploadRef.value);
                uploadRef.value.submit();
            } else {
                console.error('Upload ref not found');
            }
        };

        const handleUploadProgress = (event) => {
            console.log('Upload progress:', event.percent);
        };

        // 自定义上传请求，使用XMLHttpRequest获取真实进度
        const customUploadRequest = (options) => {
            const { file, action, headers, data, onProgress, onSuccess, onError } = options;

            const xhr = new XMLHttpRequest();
            xhr.open('POST', action, true);

            // 设置请求头
            if (headers) {
                for (const key in headers) {
                    xhr.setRequestHeader(key, headers[key]);
                }
            }

            // 上传进度事件
            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    const percent = (event.loaded / event.total) * 100;
                    onProgress({ percent });
                }
            };

            // 请求完成
            xhr.onload = () => {
                uploading.value = false;
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        const response = JSON.parse(xhr.responseText);
                        if (response.code === 200) {
                            onSuccess(response);
                        } else {
                            onError(new Error(response.message || '上传失败'));
                        }
                    } catch (e) {
                        onError(e);
                    }
                } else {
                    try {
                        const response = JSON.parse(xhr.responseText);
                        onError(new Error(response.message || '上传失败'));
                    } catch (e) {
                        onError(new Error('上传失败'));
                    }
                }
            };

            xhr.onerror = () => {
                uploading.value = false;
                onError(new Error('网络错误'));
            };

            // 构建FormData
            const formData = new FormData();
            formData.append('file', file);
            if (data) {
                for (const key in data) {
                    formData.append(key, data[key]);
                }
            }

            xhr.send(formData);

            // 保存xhr引用以便取消
            options.xhr = xhr;
        };

        const handleUploadSuccess = (response) => {
            console.log('Upload success:', response);
            uploading.value = false;
            ElementPlus.ElMessage.success('上传成功');
            // 重置上传对话框
            if (uploadRef.value) {
                uploadRef.value.clearFiles();
            }
            showUploadDialog.value = false;
            loadFiles();
        };

        const handleUploadError = (err) => {
            console.error('Upload error:', err);
            uploading.value = false;
            // 从错误响应中提取错误信息
            let errorMsg = '上传失败';
            if (err.response && err.response.data) {
                errorMsg = err.response.data.message || errorMsg;
            }
            ElementPlus.ElMessage.error(errorMsg);
            // 重置上传对话框
            if (uploadRef.value) {
                uploadRef.value.clearFiles();
            }
        };

        const cancelUpload = async () => {
            if (uploading.value) {
                try {
                    await ElementPlus.ElMessageBox.confirm(
                        '是否取消上传，这将终止进行中的上传任务和取消还未进行的任务',
                        '取消上传',
                        { confirmButtonText: '确定取消', cancelButtonText: '继续上传', type: 'warning' }
                    );
                    uploading.value = false;
                    showUploadDialog.value = false;
                    if (uploadRef.value) {
                        uploadRef.value.clearFiles();
                    }
                } catch (e) {
                    // 用户点击继续上传，不做任何操作
                }
            } else {
                showUploadDialog.value = false;
                if (uploadRef.value) {
                    uploadRef.value.clearFiles();
                }
            }
        };

        // Share management
        const loadShares = async () => {
            shareLoading.value = true;
            try {
                const res = await api.get('/share/list');
                if (res.code === 200) {
                    shareList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载分享列表失败');
            } finally {
                shareLoading.value = false;
            }
        };

        const copyShareLink = (share) => {
            const link = window.location.origin + '/share/' + share.shareCode;
            navigator.clipboard.writeText(link);
            ElementPlus.ElMessage.success('链接已复制到剪贴板');
        };

        const deleteShare = async (share) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除这个分享吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.delete('/share/delete', { params: { shareCode: share.shareCode } });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadShares();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败');
                }
            }
        };

        // User management
        const loadUsers = async () => {
            userLoading.value = true;
            try {
                const res = await api.get('/admin/users');
                if (res.code === 200) {
                    userList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载用户列表失败');
            } finally {
                userLoading.value = false;
            }
        };

        const editUser = (user) => {
            userForm.value = { ...user, roles: user.roles || [] };
            showUserDialog.value = true;
        };

        const saveUser = async () => {
            try {
                const res = userForm.value.id
                    ? await api.put('/admin/users/' + userForm.value.id, userForm.value)
                    : await api.post('/admin/users', userForm.value);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('保存成功');
                    showUserDialog.value = false;
                    loadUsers();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('保存失败');
            }
        };

        const deleteUser = async (user) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除这个用户吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.delete('/admin/users/' + user.id);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadUsers();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败');
                }
            }
        };

        // Role management
        const loadRoles = async () => {
            roleLoading.value = true;
            try {
                const res = await api.get('/admin/roles');
                if (res.code === 200) {
                    roleList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载角色列表失败');
            } finally {
                roleLoading.value = false;
            }
        };

        const editRole = (role) => {
            roleForm.value = { ...role };
            showRoleDialog.value = true;
        };

        const deleteRole = async (role) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除这个角色吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.delete('/admin/roles/' + role.id);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadRoles();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败');
                }
            }
        };

        // MinIO API Key management
        const loadMinioKeys = async () => {
            minioKeyLoading.value = true;
            try {
                const res = await api.get('/admin/minio-key/list');
                if (res.code === 200) {
                    minioKeyList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载密钥列表失败');
            } finally {
                minioKeyLoading.value = false;
            }
        };

        const createMinioKey = async () => {
            try {
                // 验证至少添加了一个桶权限
                if (!minioKeyForm.value.bucketPermissions || minioKeyForm.value.bucketPermissions.length === 0) {
                    ElementPlus.ElMessage.error('请至少添加一个桶权限');
                    return;
                }
                // 验证每个桶权限都已选择桶
                for (const bp of minioKeyForm.value.bucketPermissions) {
                    if (!bp.bucket) {
                        ElementPlus.ElMessage.error('请为每个权限选择桶');
                        return;
                    }
                }

                const res = await api.post('/admin/minio-key/create', {
                    name: minioKeyForm.value.name,
                    bucketPermissions: minioKeyForm.value.bucketPermissions,
                    expireDays: minioKeyForm.value.expireDays
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('创建成功');
                    showMinioKeyDialog.value = false;
                    // Reset form
                    minioKeyForm.value = {
                        name: '',
                        bucketPermissions: [],
                        expireDays: 0
                    };
                    // Show the created key info
                    ElementPlus.ElMessageBox.alert(
                        '密钥创建成功！\n\nAccess Key: ' + res.data.accessKey + '\nSecret Key: ' + res.data.secretKey + '\n\n请妥善保存Secret Key，因为它只会显示一次！',
                        '密钥信息',
                        { confirmButtonText: '我知道了', type: 'success' }
                    );
                    loadMinioKeys();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const copyMinioKey = async (key) => {
            // 解析桶权限信息
            let bucketInfo = '';
            if (key.bucketsJson) {
                try {
                    const perms = JSON.parse(key.bucketsJson);
                    bucketInfo = perms.map(p =>
                        `${p.bucket}: ${p.canRead ? '读' : ''}${p.canWrite ? '写' : ''}${p.canDelete ? '删' : ''}`
                    ).join(', ');
                } catch (e) {
                    bucketInfo = key.bucketsJson;
                }
            }
            const text = 'Access Key: ' + key.accessKey + '\nSecret Key: (请在创建时查看)\n桶权限: ' + bucketInfo;
            navigator.clipboard.writeText(text);
            ElementPlus.ElMessage.success('已复制到剪贴板');
        };

        const deleteMinioKey = async (key) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除这个密钥吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.delete('/admin/minio-key/' + key.id);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除成功');
                    loadMinioKeys();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败');
                }
            }
        };

        // Config
        const loadFileConfig = async () => {
            try {
                const res = await api.get('/admin/file-config');
                if (res.code === 200) {
                    const extensions = res.data.allowedExtensions || [];
                    fileConfig.value.allowedExtensionsText = extensions.join(',');
                    // -1 means unlimited
                    if (res.data.maxFileSize === -1) {
                        fileConfig.value.maxFileSizeMB = -1;
                    } else {
                        fileConfig.value.maxFileSizeMB = Math.floor((res.data.maxFileSize || 104857600) / 1024 / 1024);
                    }
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载配置失败');
            }
        };

        const saveFileConfig = async () => {
            try {
                const extensions = fileConfig.value.allowedExtensionsText
                    .split(',')
                    .map(e => e.trim())
                    .filter(e => e);
                const res = await api.post('/admin/file-config', {
                    allowedExtensions: extensions,
                    maxFileSize: fileConfig.value.maxFileSizeMB * 1024 * 1024
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('保存成功');
                }
            } catch (error) {
                ElementPlus.ElMessage.error('保存失败');
            }
        };

        // Share config
        const loadShareConfig = async () => {
            try {
                const res = await api.get('/admin/share-config');
                if (res.code === 200) {
                    shareConfig.value.batchShareMaxSizeGB = Math.floor((res.data.batchShareMaxSize || 2147483648) / 1024 / 1024 / 1024);
                    shareConfig.value.batchDownloadMaxSizeGB = Math.floor((res.data.batchDownloadMaxSize || 2147483648) / 1024 / 1024 / 1024);
                    shareConfig.value.expireHours = res.data.shareExpireHours || 168;
                }
            } catch (error) {
                // Use defaults
            }
        };

        const saveShareConfig = async () => {
            try {
                const res = await api.post('/admin/share-config', {
                    batchShareMaxSize: shareConfig.value.batchShareMaxSizeGB * 1024 * 1024 * 1024,
                    batchDownloadMaxSize: shareConfig.value.batchDownloadMaxSizeGB * 1024 * 1024 * 1024,
                    shareExpireHours: shareConfig.value.expireHours
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('保存成功');
                }
            } catch (error) {
                ElementPlus.ElMessage.error('保存失败');
            }
        };

        // Utils
        const formatSize = (bytes) => {
            if (!bytes) return '0 B';
            const units = ['B', 'KB', 'MB', 'GB', 'TB'];
            let i = 0;
            while (bytes >= 1024 && i < units.length - 1) {
                bytes /= 1024;
                i++;
            }
            return bytes.toFixed(2) + ' ' + units[i];
        };

        // Load system help
        const loadSystemHelp = async () => {
            try {
                const res = await api.get('/help/system');
                if (res.code === 200) {
                    helpContent.value = markdownToHtml(res.data);
                }
            } catch (error) {
                console.error('Failed to load system help:', error);
            }
        };

        // Load version list
        const loadVersionList = async () => {
            try {
                const res = await api.get('/help/versions');
                if (res.code === 200) {
                    versionList.value = res.data;
                    if (versionList.value.length > 0) {
                        loadVersionNote(versionList.value[0]);
                    }
                }
            } catch (error) {
                console.error('Failed to load version list:', error);
            }
        };

        // Load version note
        const loadVersionNote = async (versionInfo) => {
            selectedVersion.value = versionInfo.version;
            try {
                const res = await api.get('/help/version', {
                    params: { fileName: versionInfo.fileName }
                });
                if (res.code === 200) {
                    versionContent.value = markdownToHtml(res.data);
                }
            } catch (error) {
                console.error('Failed to load version note:', error);
            }
        };

        // Simple markdown to HTML converter
        const markdownToHtml = (markdown) => {
            if (!markdown) return '';

            let html = markdown
                .replace(/^### (.*$)/gim, '<h3>$1</h3>')
                .replace(/^## (.*$)/gim, '<h2>$1</h2>')
                .replace(/^# (.*$)/gim, '<h1>$1</h1>')
                .replace(/\*\*(.*)\*\*/gim, '<strong>$1</strong>')
                .replace(/\*(.*)\*/gim, '<em>$1</em>')
                .replace(/^- (.*$)/gim, '<li>$1</li>')
                .replace(/^(\d+)\. (.*$)/gim, '<li>$2</li>')
                .replace(/\n\n/g, '</p><p>')
                .replace(/\n/g, '<br>');

            // Wrap with paragraphs
            if (!html.startsWith('<h') && !html.startsWith('<p')) {
                html = '<p>' + html + '</p>';
            }

            // Handle list items
            html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');

            return html;
        };

        // Watch help dialog
        watch(showHelpDialog, (newVal) => {
            if (newVal && !helpContent.value) {
                loadSystemHelp();
            }
        });

        // Watch version dialog
        watch(showVersionDialog, (newVal) => {
            if (newVal && versionList.value.length === 0) {
                loadVersionList();
            }
        });

        // Lifecycle
        onMounted(() => {
            checkAuth();
        });

        return {
            isLoggedIn,
            token,
            userInfo,
            currentMenu,
            loginForm,
            loginLoading,
            bucketList,
            bucketLoading,
            currentBucket,
            showBucketDialog,
            newBucketName,
            bucketSortBy,
            bucketOrder,
            fileList,
            fileLoading,
            fileSortBy,
            fileOrder,
            filePage,
            filePageSize,
            fileTotal,
            handleFileSizeChange,
            handleFilePageChange,
            currentFolder,
            breadcrumb,
            selectedFiles,
            showUploadDialog,
            uploadRef,
            showFolderDialog,
            showShareDialog,
            showUserDialog,
            showRoleDialog,
            showMinioKeyDialog,
            showHelpDialog,
            showVersionDialog,
            helpContent,
            versionList,
            selectedVersion,
            versionContent,
            loadVersionNote,
            minioKeyForm,
            minioKeyList,
            minioKeyLoading,
            addBucketPermission,
            removeBucketPermission,
            newFolderName,
            createTestFile,
            shareForm,
            userForm,
            roleForm,
            shareList,
            userList,
            roleList,
            shareLoading,
            userLoading,
            roleLoading,
            fileConfig,
            shareConfig,
            isAdmin,
            uploadUrl,
            uploadHeaders,
            uploadData,
            handleLogin,
            logout,
            handleCommand,
            handleMenuSelect,
            loadBuckets,
            selectBucket,
            createBucket,
            deleteBucket,
            handleBucketSortChange,
            loadFiles,
            handleFileSortChange,
            refreshFiles,
            openUploadDialog,
            navigateToFolder,
            downloadFile,
            previewFile,
            deleteFile,
            batchShare,
            batchDownload,
            batchDelete,
            createFolder,
            shareFile,
            createShare,
            handleSelectionChange,
            submitUpload,
            handleUploadSuccess,
            handleUploadError,
            handleUploadProgress,
            customUploadRequest,
            cancelUpload,
            uploading,
            copyShareLink,
            deleteShare,
            editUser,
            saveUser,
            deleteUser,
            editRole,
            deleteRole,
            loadMinioKeys,
            createMinioKey,
            copyMinioKey,
            deleteMinioKey,
            loadFileConfig,
            saveFileConfig,
            loadShareConfig,
            saveShareConfig,
            formatSize
        };
    }
});

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
}

app.use(ElementPlus);
app.mount('#app');
