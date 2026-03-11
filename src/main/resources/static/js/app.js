const { createApp, ref, computed, onMounted } = Vue;

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

        // Dialogs
        const showUploadDialog = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);
        const showUserDialog = ref(false);
        const showRoleDialog = ref(false);

        // Form data
        const newFolderName = ref('');
        const shareForm = ref({ objectName: '', expirationHours: 24, password: '' });
        const userForm = ref({});
        const roleForm = ref({});

        // Lists
        const shareList = ref([]);
        const userList = ref([]);
        const roleList = ref([]);

        // Loading states
        const shareLoading = ref(false);
        const userLoading = ref(false);
        const roleLoading = ref(false);

        // File config
        const fileConfig = ref({
            allowedExtensionsText: '',
            maxFileSizeMB: 100
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

        // SHA512 hash function
        const sha512 = async (message) => {
            const msgBuffer = new TextEncoder().encode(message);
            const hashBuffer = await crypto.subtle.digest('SHA-512', msgBuffer);
            const hashArray = Array.from(new Uint8Array(hashBuffer));
            return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
        };

        // Methods
        const handleLogin = async () => {
            loginLoading.value = true;
            try {
                const hashedPassword = await sha512(loginForm.value.password);
                const res = await api.post('/auth/login', {
                    username: loginForm.value.username,
                    password: hashedPassword
                });
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
                ElementPlus.ElMessage.error(error.response?.data?.message || '登录失败');
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
                        order: fileOrder.value
                    }
                });
                if (res.code === 200) {
                    fileList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载文件列表失败');
            } finally {
                fileLoading.value = false;
            }
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

        const createFolder = async () => {
            if (!newFolderName.value) {
                ElementPlus.ElMessage.warning('请输入文件夹名称');
                return;
            }
            try {
                const path = currentFolder.value + newFolderName.value;
                const res = await api.post('/file/folder', null, {
                    params: { path: path, bucket: currentBucket.value }
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('创建成功');
                    showFolderDialog.value = false;
                    newFolderName.value = '';
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
                password: ''
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
                    ElementPlus.ElMessage.success('分享创建成功');
                    showShareDialog.value = false;
                }
            } catch (error) {
                ElementPlus.ElMessage.error('创建分享失败');
            }
        };

        const handleSelectionChange = (selection) => {
            selectedFiles.value = selection;
        };

        const submitUpload = () => {
            const uploadInstance = document.querySelector('.el-upload__input');
            if (uploadInstance && uploadInstance.files.length > 0) {
                const formData = new FormData();
                for (const file of uploadInstance.files) {
                    formData.append('file', file);
                }
                formData.append('folder', currentFolder.value);
                formData.append('bucket', currentBucket.value);
                api.post('/file/upload', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                }).then(res => {
                    if (res.code === 200) {
                        ElementPlus.ElMessage.success('上传成功');
                        showUploadDialog.value = false;
                        loadFiles();
                    }
                }).catch(() => {
                    ElementPlus.ElMessage.error('上传失败');
                });
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

        // Config
        const loadFileConfig = async () => {
            try {
                const res = await api.get('/admin/file-config');
                if (res.code === 200) {
                    const extensions = res.data.allowedExtensions || [];
                    fileConfig.value.allowedExtensionsText = extensions.join(',');
                    fileConfig.value.maxFileSizeMB = Math.floor((res.data.maxFileSize || 104857600) / 1024 / 1024);
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
            currentFolder,
            breadcrumb,
            selectedFiles,
            showUploadDialog,
            showFolderDialog,
            showShareDialog,
            showUserDialog,
            showRoleDialog,
            newFolderName,
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
            navigateToFolder,
            downloadFile,
            previewFile,
            deleteFile,
            createFolder,
            shareFile,
            createShare,
            handleSelectionChange,
            submitUpload,
            copyShareLink,
            deleteShare,
            editUser,
            saveUser,
            deleteUser,
            editRole,
            deleteRole,
            loadFileConfig,
            saveFileConfig,
            formatSize
        };
    }
});

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
}

app.use(ElementPlus);
app.mount('#app');
