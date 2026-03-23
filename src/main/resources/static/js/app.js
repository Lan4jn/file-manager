const { createApp, ref, computed, onMounted, onUnmounted, nextTick, watch } = Vue;

const zhCnLocale = {
    name: 'zh-cn',
    el: {
        pagination: {
            goto: '前往',
            pagesize: '条/页',
            total: '共 {total} 条',
            pageClassifier: '页',
            prev: '上一页',
            next: '下一页',
            currentPage: '第 {pager} 页',
            prevPages: '向前 {pager} 页',
            nextPages: '向后 {pager} 页',
            deprecationWarning: '已检测到已废弃的用法，请参考 el-pagination 文档'
        }
    }
};

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
        // 导航历史
        const folderHistory = ref([]);
        const historyIndex = ref(-1);
        const showPathDropdown = ref(false);

        // 地址栏编辑相关
        const isPathEditing = ref(false);
        const editPathValue = ref('');

        // Dialogs
        const showUploadDialog = ref(false);
        const uploadRef = ref(null);
        const uploading = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);
        const showBatchShareDialog = ref(false);
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
        const batchShareForm = ref({ expirationHours: 24, password: '' });
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

        const currentViewTitle = computed(() => {
            const titles = {
                buckets: '存储空间',
                files: '我的文件',
                shares: '共享中心',
                users: '团队成员',
                roles: '权限角色',
                'minio-keys': '访问密钥',
                config: '系统设置'
            };
            return titles[currentMenu.value] || '文件管理';
        });

        const currentViewDescription = computed(() => {
            const descriptions = {
                buckets: '统一管理对象存储桶与容量分布。',
                files: '按目录组织文件，上传、分享、下载一体化处理。',
                shares: '集中查看外链、提取码与访问记录。',
                users: '维护系统用户、启停状态与角色分配。',
                roles: '定义可复用的权限集合。',
                'minio-keys': '发放受限访问凭据并控制桶级权限。',
                config: '收口上传、分享和下载策略。'
            };
            return descriptions[currentMenu.value] || '现代化文件工作台。';
        });

        const bucketCount = computed(() => bucketList.value.length);
        const folderCount = computed(() => fileList.value.filter(item => item.isFolder).length);
        const currentFileCount = computed(() => fileList.value.filter(item => !item.isFolder).length);
        const currentPageSize = computed(() => {
            return fileList.value.reduce((total, item) => total + (item.isFolder ? 0 : (item.size || 0)), 0);
        });
        const currentPathLabel = computed(() => {
            if (!currentBucket.value) {
                return '未选择存储桶';
            }
            if (!currentFolder.value) {
                return currentBucket.value + ' / 根目录';
            }
            return currentBucket.value + ' / ' + currentFolder.value;
        });

        const copyText = async (text) => {
            if (!text) {
                throw new Error('复制内容为空');
            }

            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
                return;
            }

            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.setAttribute('readonly', 'readonly');
            textarea.style.position = 'fixed';
            textarea.style.left = '-9999px';
            textarea.style.top = '0';
            document.body.appendChild(textarea);
            textarea.focus();
            textarea.select();

            try {
                const copied = document.execCommand('copy');
                if (!copied) {
                    throw new Error('当前环境不支持自动复制');
                }
            } finally {
                document.body.removeChild(textarea);
            }
        };

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

        const onBucketSelect = () => {
            // 清空历史重新开始
            folderHistory.value = [];
            historyIndex.value = -1;
            currentFolder.value = '';
            updateBreadcrumb();
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

        // 导航输入路径（计算属性）
        const currentPathInput = computed({
            get: () => {
                if (!currentBucket.value) return '';
                if (!currentFolder.value) return `/${currentBucket.value}/`;
                return `/${currentBucket.value}/${currentFolder.value}`;
            },
            set: (val) => {}
        });

        const applyPathNavigation = (rawPath) => {
            const path = (rawPath || '').trim();
            if (!path) return;

            // 解析输入路径，格式：/bucket/folder1/folder2/ 或 bucket/folder1/folder2/
            const parts = path.split('/').filter(p => p);
            if (parts.length === 0) return;

            const bucket = parts[0];
            if (bucket) {
                currentBucket.value = bucket;
            }

            if (parts.length > 1) {
                currentFolder.value = parts.slice(1).join('/') + '/';
            } else {
                currentFolder.value = '';
            }

            // 清空历史重新开始
            folderHistory.value = [];
            historyIndex.value = -1;

            updateBreadcrumb();
            loadFiles();
            loadBuckets();
        };

        // 从输入框导航
        const navigateFromInput = () => {
            applyPathNavigation(currentPathInput.value);
        };

        // 触发地址栏编辑模式
        const startPathEdit = () => {
            isPathEditing.value = true;
            editPathValue.value = currentPathInput.value;
            nextTick(() => {
                const pathInput = document.querySelector('.path-edit-input');
                if (pathInput) {
                    pathInput.focus();
                    pathInput.select();
                }
            });
        };

        // 确认编辑路径
        const confirmPathEdit = () => {
            const path = editPathValue.value.trim();
            if (!path) {
                cancelPathEdit();
                return;
            }

            applyPathNavigation(path);
            isPathEditing.value = false;
        };

        // 取消编辑模式
        const cancelPathEdit = () => {
            isPathEditing.value = false;
            editPathValue.value = '';
        };

        // 点击路径节点跳转
        const navigateToNode = (node) => {
            if (node.isBucket) {
                currentFolder.value = '';
            } else {
                currentFolder.value = node.path;
            }

            // 清空历史重新开始
            folderHistory.value = [];
            historyIndex.value = -1;

            updateBreadcrumb();
            loadFiles();
        };

        // 后退
        const canGoBack = computed(() => historyIndex.value > 0);
        const goBack = () => {
            if (!canGoBack.value) return;
            historyIndex.value--;
            currentFolder.value = folderHistory.value[historyIndex.value];
            updateBreadcrumb();
            loadFiles();
        };

        // 前进
        const canGoForward = computed(() => historyIndex.value < folderHistory.value.length - 1);
        const goForward = () => {
            if (!canGoForward.value) return;
            historyIndex.value++;
            currentFolder.value = folderHistory.value[historyIndex.value];
            updateBreadcrumb();
            loadFiles();
        };

        // 向上
        const goUp = () => {
            if (!currentFolder.value) return;
            const parts = currentFolder.value.split('/').filter(p => p);
            if (parts.length === 0) {
                navigateToFolder('');
            } else {
                parts.pop();
                navigateToFolder(parts.length > 0 ? parts.join('/') + '/' : '');
            }
        };

        const navigateToFolder = (path) => {
            const oldPath = currentFolder.value;
            currentFolder.value = path || '';

            // 记录历史
            if (historyIndex.value < folderHistory.value.length - 1) {
                // 如果在历史中间，截断后面的历史
                folderHistory.value = folderHistory.value.slice(0, historyIndex.value + 1);
            }
            folderHistory.value.push(currentFolder.value);
            historyIndex.value = folderHistory.value.length - 1;

            updateBreadcrumb();
            loadFiles();
        };

        const updateBreadcrumb = () => {
            const crumbs = [];

            // 首先添加 bucket 节点
            if (currentBucket.value) {
                crumbs.push({
                    name: currentBucket.value,
                    path: currentBucket.value + '/',
                    isBucket: true,
                    isFolder: false
                });
            }

            // 然后添加 folder 节点
            if (currentFolder.value) {
                const parts = currentFolder.value.split('/').filter(p => p);
                let path = '';
                for (const part of parts) {
                    path += part + '/';
                    crumbs.push({
                        name: part,
                        path: path,
                        isBucket: false,
                        isFolder: true
                    });
                }
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
            batchShareForm.value = {
                expirationHours: shareConfig.value.expireHours || 24,
                password: ''
            };
            showBatchShareDialog.value = true;
        };

        const createBatchShare = async () => {
            try {
                const objectNames = selectedFiles.value.map(f => f.objectName);
                const res = await api.post('/share/batch-create', {
                    objectNames: objectNames,
                    bucket: currentBucket.value,
                    expirationHours: batchShareForm.value.expirationHours,
                    password: batchShareForm.value.password
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('分享创建成功');
                    const link = window.location.origin + '/share/' + res.data.shareCode;
                    try {
                        await copyText(link);
                        ElementPlus.ElMessage.info('分享链接已复制到剪贴板');
                    } catch (copyError) {
                        ElementPlus.ElMessage.warning('分享已创建，但自动复制失败：' + copyError.message);
                    }
                    showBatchShareDialog.value = false;
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建分享失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const batchDownloadFromDialog = async () => {
            showBatchShareDialog.value = false;
            await batchDownload();
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
                        successMsg += '（支持在线浏览当前目录内容）';
                    }
                    ElementPlus.ElMessage.success(successMsg);
                    const link = window.location.origin + '/share/' + res.data.shareCode;
                    try {
                        await copyText(link);
                        ElementPlus.ElMessage.info('分享链接已复制到剪贴板');
                    } catch (copyError) {
                        ElementPlus.ElMessage.warning('分享已创建，但自动复制失败：' + copyError.message);
                    }
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
                    shareList.value = (res.data || []).map(item => ({
                        ...item,
                        createTimeText: formatDateTime(item.createTime),
                        expireTimeText: formatDateTime(item.expireTime),
                        hasPasswordText: item.hasPassword ? '有密码' : '公开访问',
                        shareUrl: window.location.origin + '/share/' + item.shareCode,
                        shareItems: [],
                        shareItemsLoaded: false,
                        shareItemsLoading: false
                    }));
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载分享列表失败');
            } finally {
                shareLoading.value = false;
            }
        };

        const copyShareLink = (share) => {
            const link = window.location.origin + '/share/' + share.shareCode;
            copyText(link)
                .then(() => {
                    ElementPlus.ElMessage.success('链接已复制到剪贴板');
                })
                .catch((error) => {
                    ElementPlus.ElMessage.error('复制失败: ' + error.message);
                });
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
                    ElementPlus.ElMessage.error('删除失败: ' + (error.response?.data?.message || error.message));
                }
            }
        };

        const loadShareItemsForManage = async (share) => {
            if (!share || share.shareItemsLoading || share.shareItemsLoaded || !share.isFolder) {
                return;
            }
            share.shareItemsLoading = true;
            try {
                const res = await api.get('/share/items', {
                    params: {
                        shareCode: share.shareCode,
                        password: share.password || ''
                    }
                });
                if (res.code === 200) {
                    share.shareItems = res.data || [];
                    share.shareItemsLoaded = true;
                }
            } catch (error) {
                share.shareItems = [];
                ElementPlus.ElMessage.error('加载分享内容失败: ' + (error.response?.data?.message || error.message));
            } finally {
                share.shareItemsLoading = false;
            }
        };

        const handleShareExpand = (row, expandedRows) => {
            const expanded = (expandedRows || []).some(item => item.shareCode === row.shareCode);
            if (expanded) {
                loadShareItemsForManage(row);
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

        const formatDateTime = (value) => {
            if (!value) return '-';
            const date = new Date(value);
            if (Number.isNaN(date.getTime())) {
                return value;
            }
            const pad = (num) => String(num).padStart(2, '0');
            return date.getFullYear() + '-' +
                pad(date.getMonth() + 1) + '-' +
                pad(date.getDate()) + ' ' +
                pad(date.getHours()) + ':' +
                pad(date.getMinutes()) + ':' +
                pad(date.getSeconds());
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

        const handleDocumentClick = (e) => {
            const pathStrip = document.querySelector('.path-strip');
            const pathInput = document.querySelector('.path-edit-input');
            if (isPathEditing.value && pathStrip && pathInput) {
                if (!pathStrip.contains(e.target) && e.target !== pathInput) {
                    cancelPathEdit();
                }
            }
        };

        // Lifecycle
        onMounted(() => {
            checkAuth();
            document.addEventListener('click', handleDocumentClick);
        });

        onUnmounted(() => {
            document.removeEventListener('click', handleDocumentClick);
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
            showBatchShareDialog,
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
            batchShareForm,
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
            currentViewTitle,
            currentViewDescription,
            bucketCount,
            folderCount,
            currentFileCount,
            currentPageSize,
            currentPathLabel,
            currentPathInput,
            showPathDropdown,
            canGoBack,
            canGoForward,
            goBack,
            goForward,
            goUp,
            navigateFromInput,
            navigateToNode,
            isPathEditing,
            editPathValue,
            startPathEdit,
            confirmPathEdit,
            cancelPathEdit,
            uploadUrl,
            uploadHeaders,
            uploadData,
            handleLogin,
            logout,
            handleCommand,
            handleMenuSelect,
            loadBuckets,
            selectBucket,
            onBucketSelect,
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
            createBatchShare,
            batchDownload,
            batchDownloadFromDialog,
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
            handleShareExpand,
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
            formatSize,
            formatDateTime
        };
    }
});

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
}

app.use(ElementPlus, { locale: zhCnLocale });
app.mount('#app');
