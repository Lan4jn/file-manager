// Lite JS - 低GPU轻量界面脚本
// 简化版核心功能，无动画特效

const { createApp, ref, computed, onMounted } = Vue;

const api = axios.create({
    baseURL: '/api',
    timeout: 30000
});

// 请求拦截器
api.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器
api.interceptors.response.use(
    response => response.data,
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.reload();
        }
        return Promise.reject(error);
    }
);

createApp({
    setup() {
        // Login
        const isLoggedIn = ref(false);
        const loginForm = ref({ username: '', password: '' });
        const loginLoading = ref(false);

        // User
        const userInfo = ref({});

        // Buckets
        const bucketList = ref([]);
        const currentBucket = ref('');

        // Files
        const fileList = ref([]);
        const fileTotal = ref(0);
        const filePage = ref(1);
        const filePageSize = ref(10);
        const fileSortBy = ref('name');
        const fileOrder = ref('asc');
        const currentFolder = ref('');

        // Selection
        const selectedFiles = ref([]);

        // Dialogs
        const showUploadDialog = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);

        // Upload
        const uploadQueue = ref([]);
        const uploadProgress = ref(0);

        // Folder
        const newFolderName = ref('');

        // Share
        const shareFileName = ref('');
        const shareObjectName = ref('');
        const shareExpireHours = ref(24);
        const sharePassword = ref('');
        const shareLink = ref('');
        const runtimeConfig = ref({ httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' });
        const showHttpsNotice = computed(() => window.location.protocol === 'http:' && runtimeConfig.value.forceHttps);

        // Computed
        const totalPages = computed(() => Math.ceil(fileTotal.value / filePageSize.value) || 1);
        const currentPath = computed(() => {
            if (!currentBucket.value) {
                return '/';
            }
            return currentFolder.value
                ? `/${currentBucket.value}/${currentFolder.value}`
                : `/${currentBucket.value}/`;
        });
        const httpsSuggestionUrl = computed(() => {
            const url = new URL(window.location.href);
            url.protocol = 'https:';

            if (runtimeConfig.value.httpsPort) {
                url.port = String(runtimeConfig.value.httpsPort) === '443' ? '' : String(runtimeConfig.value.httpsPort);
            }

            return url.toString();
        });
        const displayVersionLabel = computed(() => {
            return runtimeConfig.value.appVersion ? `v${runtimeConfig.value.appVersion}` : '版本';
        });

        const ensureFolderFirst = (items) => {
            return (items || []).slice().sort((a, b) => {
                const aIsFolder = !!a.isFolder;
                const bIsFolder = !!b.isFolder;
                if (aIsFolder === bIsFolder) {
                    return 0;
                }
                return aIsFolder ? -1 : 1;
            });
        };

        // Methods
        const handleLogin = async () => {
            if (!loginForm.value.username || !loginForm.value.password) return;
            loginLoading.value = true;
            try {
                const password = btoa(unescape(encodeURIComponent(loginForm.value.password)));
                const res = await api.post('/auth/login', { username: loginForm.value.username, password });
                if (res.code === 200) {
                    localStorage.setItem('token', res.data.token);
                    localStorage.setItem('user', JSON.stringify(res.data));
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    loadBuckets();
                }
            } catch (e) {
                alert('登录失败');
            } finally {
                loginLoading.value = false;
            }
        };

        const handleLogout = () => {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            isLoggedIn.value = false;
            loginForm.value = { username: '', password: '' };
        };

        const loadRuntimeConfig = async () => {
            try {
                const res = await api.get('/auth/runtime-config');
                if (res.code === 200) {
                    runtimeConfig.value = res.data || { httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' };
                }
            } catch (e) {
                // Ignore and fallback to current URL scheme replacement.
            }
        };

        const loadBuckets = async () => {
            try {
                const res = await api.get('/file/buckets', { params: { sortBy: 'name', order: 'asc' } });
                if (res.code === 200) {
                    bucketList.value = res.data || [];
                    if (bucketList.value.length > 0) {
                        currentBucket.value = bucketList.value[0].name;
                        currentFolder.value = '';
                        loadFiles();
                    }
                }
            } catch (e) {
                console.error('Failed to load buckets:', e);
            }
        };

        const loadFiles = async () => {
            try {
                const res = await api.get('/file/list', {
                    params: {
                        bucket: currentBucket.value,
                        folder: currentFolder.value,
                        sortBy: fileSortBy.value,
                        order: fileOrder.value,
                        page: filePage.value,
                        size: filePageSize.value
                    }
                });
                if (res.data.items !== undefined) {
                    fileList.value = ensureFolderFirst(res.data.items);
                    fileTotal.value = res.data.total || 0;
                } else {
                    fileList.value = ensureFolderFirst(res.data);
                    fileTotal.value = res.data.length || 0;
                }
                selectedFiles.value = [];
            } catch (e) {
                console.error('Failed to load files:', e);
            }
        };

        const refreshFiles = () => {
            loadFiles();
        };

        const onBucketChange = () => {
            filePage.value = 1;
            currentFolder.value = '';
            loadFiles();
        };

        const navigateToFolder = (folderPath) => {
            currentFolder.value = folderPath || '';
            filePage.value = 1;
            loadFiles();
        };

        const toggleSelect = (file) => {
            const index = selectedFiles.value.indexOf(file.objectName);
            if (index === -1) {
                selectedFiles.value.push(file.objectName);
            } else {
                selectedFiles.value.splice(index, 1);
            }
        };

        const toggleSelectAll = (e) => {
            if (e.target.checked) {
                selectedFiles.value = fileList.value.map(f => f.objectName);
            } else {
                selectedFiles.value = [];
            }
        };

        const prevPage = () => {
            if (filePage.value > 1) {
                filePage.value--;
                loadFiles();
            }
        };

        const nextPage = () => {
            if (filePage.value < totalPages.value) {
                filePage.value++;
                loadFiles();
            }
        };

        const onPageSizeChange = () => {
            filePage.value = 1;
            loadFiles();
        };

        // Upload
        const handleFileSelect = (e) => {
            uploadQueue.value = Array.from(e.target.files);
        };

        const submitUpload = async () => {
            if (uploadQueue.value.length === 0) return;
            uploadProgress.value = 0;

            for (let i = 0; i < uploadQueue.value.length; i++) {
                const file = uploadQueue.value[i];
                const formData = new FormData();
                formData.append('file', file);
                formData.append('bucket', currentBucket.value);

                try {
                    await api.post('/file/upload', formData, {
                        headers: { 'Content-Type': 'multipart/form-data' },
                        onUploadProgress: (e) => {
                            if (e.total) {
                                uploadProgress.value = Math.round((e.loaded / e.total) * 100);
                            }
                        }
                    });
                } catch (e) {
                    alert(`上传失败: ${file.name}`);
                }
            }

            uploadQueue.value = [];
            uploadProgress.value = 0;
            showUploadDialog.value = false;
            loadFiles();
        };

        // Folder
        const createFolder = async () => {
            if (!newFolderName.value) return;
            try {
                const path = `${currentFolder.value}${newFolderName.value}`;
                await api.post('/file/folder', null, {
                    params: {
                        path,
                        bucket: currentBucket.value,
                        createTestFile: false
                    }
                });
                newFolderName.value = '';
                showFolderDialog.value = false;
                loadFiles();
            } catch (e) {
                alert('创建文件夹失败');
            }
        };

        // Share
        const shareFile = (file) => {
            shareFileName.value = file.fileName;
            shareObjectName.value = file.objectName;
            shareExpireHours.value = 24;
            sharePassword.value = '';
            shareLink.value = '';
            showShareDialog.value = true;
        };

        const batchShare = () => {
            if (selectedFiles.value.length > 0) {
                shareFileName.value = `已选择 ${selectedFiles.value.length} 个文件`;
                shareObjectName.value = selectedFiles.value.join(',');
                shareExpireHours.value = 24;
                sharePassword.value = '';
                shareLink.value = '';
                showShareDialog.value = true;
            }
        };

        const createShare = async () => {
            try {
                const isBatchShare = selectedFiles.value.length > 1
                    && shareObjectName.value === selectedFiles.value.join(',');
                const payload = isBatchShare
                    ? {
                        objectNames: [...selectedFiles.value],
                        bucket: currentBucket.value,
                        expirationHours: shareExpireHours.value,
                        password: sharePassword.value
                    }
                    : {
                        objectName: shareObjectName.value,
                        bucket: currentBucket.value,
                        expirationHours: shareExpireHours.value,
                        password: sharePassword.value
                    };
                const apiPath = isBatchShare ? '/share/batch-create' : '/share/create';
                const res = await api.post(apiPath, payload);
                if (res.code === 200) {
                    shareLink.value = `${window.location.origin}/share/${res.data.shareCode}`;
                }
            } catch (e) {
                alert('创建分享失败');
            }
        };

        const copyShareLink = async () => {
            if (!shareLink.value) return;
            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(shareLink.value);
                } else {
                    const textarea = document.createElement('textarea');
                    textarea.value = shareLink.value;
                    textarea.setAttribute('readonly', 'readonly');
                    textarea.style.position = 'fixed';
                    textarea.style.left = '-9999px';
                    document.body.appendChild(textarea);
                    textarea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textarea);
                }
                alert('链接已复制');
            } catch (e) {
                alert('复制失败，请手动复制链接');
            }
        };

        // Download
        const downloadFile = (file) => {
            window.open(`/api/file/download?objectName=${encodeURIComponent(file.objectName)}&bucket=${currentBucket.value}`, '_blank');
        };

        const batchDownload = () => {
            if (selectedFiles.value.length === 1) {
                downloadFile({ objectName: selectedFiles.value[0] });
            } else if (selectedFiles.value.length > 1) {
                const objectNames = selectedFiles.value.map(name => encodeURIComponent(name)).join(',');
                window.location.href = `/api/file/batch-download?objectNames=${objectNames}&bucket=${encodeURIComponent(currentBucket.value)}`;
            }
        };

        // Delete
        const deleteFile = async (file) => {
            if (!confirm(`确定删除 ${file.fileName}？`)) return;
            try {
                await api.delete('/file/delete', { params: { objectName: file.objectName, bucket: currentBucket.value } });
                loadFiles();
            } catch (e) {
                alert('删除失败');
            }
        };

        const batchDelete = async () => {
            if (!confirm(`确定删除 ${selectedFiles.value.length} 个文件？`)) return;
            try {
                await api.delete('/file/batch-delete', { params: { objectNames: selectedFiles.value.join(','), bucket: currentBucket.value } });
                selectedFiles.value = [];
                loadFiles();
            } catch (e) {
                alert('删除失败');
            }
        };

        // Preview (simplified)
        const previewFile = (file) => {
            window.open(`/api/file/preview?objectName=${encodeURIComponent(file.objectName)}&bucket=${currentBucket.value}`, '_blank');
        };

        // Utils
        const formatSize = (bytes) => {
            if (!bytes) return '0 B';
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
            if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB';
            return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
        };

        const formatDate = (dateStr) => {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleString('zh-CN');
        };

        const currentViewTitle = computed(() => {
            return '我的文件';
        });

        // Init
        onMounted(() => {
            loadRuntimeConfig();
            const token = localStorage.getItem('token');
            const user = localStorage.getItem('user');
            if (token && user) {
                userInfo.value = JSON.parse(user);
                isLoggedIn.value = true;
                loadBuckets();
            }
        });

        return {
            // Login
            isLoggedIn,
            loginForm,
            loginLoading,
            handleLogin,
            handleLogout,
            // User
            userInfo,
            // Buckets
            bucketList,
            currentBucket,
            onBucketChange,
            // Files
            fileList,
            fileTotal,
            filePage,
            filePageSize,
            fileSortBy,
            fileOrder,
            currentFolder,
            // Selection
            selectedFiles,
            toggleSelect,
            toggleSelectAll,
            // Dialogs
            showUploadDialog,
            showFolderDialog,
            showShareDialog,
            // Upload
            uploadQueue,
            uploadProgress,
            handleFileSelect,
            submitUpload,
            // Folder
            newFolderName,
            createFolder,
            // Share
            shareFileName,
            shareObjectName,
            shareExpireHours,
            sharePassword,
            shareLink,
            showHttpsNotice,
            httpsSuggestionUrl,
            displayVersionLabel,
            shareFile,
            batchShare,
            createShare,
            copyShareLink,
            // Download
            downloadFile,
            batchDownload,
            // Delete
            deleteFile,
            batchDelete,
            // Preview
            previewFile,
            // Navigation
            navigateToFolder,
            refreshFiles,
            // Pagination
            prevPage,
            nextPage,
            onPageSizeChange,
            totalPages,
            currentPath,
            // Utils
            formatSize,
            formatDate,
            currentViewTitle
        };
    }
}).mount('#app');
