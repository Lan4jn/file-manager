const { createApp, ref, computed, onMounted } = Vue;
const loginErrorHelpers = window.LoginErrorHelpers || {
    extractLoginErrorMessage: (error) => {
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
};

const api = axios.create({
    baseURL: '/api',
    timeout: 30000
});

const sha512 = (message) => window.sha512(message);
const getBcrypt = () => window.dcodeIO?.bcrypt || window.bcrypt;
const deriveLoginSecret = (password, challengeData) => {
    if ((challengeData?.passwordScheme || '').toUpperCase() === 'BCRYPT') {
        const verifier = challengeData.passwordVerifier;
        if (!verifier) {
            throw new Error('登录验证参数缺失，请刷新后重试');
        }
        const bcrypt = getBcrypt();
        if (!bcrypt || typeof bcrypt.hashSync !== 'function') {
            throw new Error('登录组件未正确加载，请刷新页面后重试');
        }
        return bcrypt.hashSync(password, verifier);
    }
    return sha512(password);
};

api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

api.interceptors.response.use(
    (response) => response.data,
    (error) => {
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
        const isLoggedIn = ref(false);
        const loginForm = ref({ username: '', password: '' });
        const loginLoading = ref(false);
        const userInfo = ref({});

        const bucketList = ref([]);
        const currentBucket = ref('');

        const fileList = ref([]);
        const fileTotal = ref(0);
        const filePage = ref(1);
        const filePageSize = ref(10);
        const fileSortBy = ref('name');
        const fileOrder = ref('asc');
        const currentFolder = ref('');

        const selectedFiles = ref([]);
        const showUploadDialog = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);

        const uploadQueue = ref([]);
        const uploadProgress = ref(0);

        const newFolderName = ref('');

        const shareFileName = ref('');
        const shareObjectName = ref('');
        const shareExpireHours = ref(24);
        const sharePassword = ref('');
        const shareLink = ref('');

        const runtimeConfig = ref({ httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' });
        const showHttpsNotice = computed(() => window.location.protocol === 'http:' && runtimeConfig.value.forceHttps);

        const totalPages = computed(() => Math.ceil(fileTotal.value / filePageSize.value) || 1);
        const selectedCount = computed(() => selectedFiles.value.length);
        const folderCount = computed(() => fileList.value.filter((file) => !!file.isFolder).length);
        const currentFileCount = computed(() => fileList.value.filter((file) => !file.isFolder).length);
        const canGoUp = computed(() => !!currentFolder.value);
        const allSelected = computed(() => fileList.value.length > 0 && selectedFiles.value.length === fileList.value.length);
        const currentPath = computed(() => {
            if (!currentBucket.value) {
                return '/';
            }
            return currentFolder.value
                ? `/${currentBucket.value}/${currentFolder.value}`
                : `/${currentBucket.value}/`;
        });
        const breadcrumbItems = computed(() => {
            const items = [];
            if (currentBucket.value) {
                items.push({ label: currentBucket.value, path: '', isBucket: true });
            }
            const segments = (currentFolder.value || '').split('/').filter(Boolean);
            let path = '';
            segments.forEach((segment) => {
                path += `${segment}/`;
                items.push({ label: segment, path, isBucket: false });
            });
            return items;
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
        const currentViewTitle = computed(() => '文件管理器');
        const currentViewSubtitle = computed(() => {
            if (!currentBucket.value) {
                return '选择存储桶后开始浏览、上传、下载和分享文件';
            }
            return currentFolder.value
                ? `当前目录 ${currentPath.value}`
                : `当前位于 ${currentBucket.value} 的根目录`;
        });
        const selectionSummary = computed(() => {
            if (!selectedCount.value) {
                return '未选择文件';
            }
            return `已选择 ${selectedCount.value} 项`;
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

        const openFullMode = () => {
            window.location.href = '/full';
        };

        const handleLogin = async () => {
            if (!loginForm.value.username || !loginForm.value.password) {
                alert('请输入用户名和密码');
                return;
            }
            loginLoading.value = true;
            try {
                const challengeRes = await api.get('/auth/login-challenge', {
                    params: { username: loginForm.value.username }
                });
                const challengeData = challengeRes.data || {};
                const loginSecret = deriveLoginSecret(loginForm.value.password, challengeData);
                const passwordProof = sha512(loginSecret + ':' + challengeData.nonce);
                const res = await api.post('/auth/login', {
                    username: loginForm.value.username,
                    challengeId: challengeData.challengeId,
                    passwordProof: passwordProof
                });
                if (res.code === 200) {
                    localStorage.setItem('token', res.data.token);
                    localStorage.setItem('user', JSON.stringify(res.data));
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    await loadBuckets();
                } else {
                    alert(res.message || '登录失败');
                }
            } catch (error) {
                alert(loginErrorHelpers.extractLoginErrorMessage(error));
            } finally {
                loginLoading.value = false;
            }
        };

        const handleLogout = () => {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            isLoggedIn.value = false;
            userInfo.value = {};
            loginForm.value = { username: '', password: '' };
        };

        const loadRuntimeConfig = async () => {
            try {
                const res = await api.get('/auth/runtime-config');
                if (res.code === 200) {
                    runtimeConfig.value = res.data || { httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' };
                }
            } catch (error) {
                console.error('Failed to load runtime config:', error);
            }
        };

        const loadBuckets = async () => {
            try {
                const res = await api.get('/file/buckets', { params: { sortBy: 'name', order: 'asc' } });
                if (res.code === 200) {
                    bucketList.value = res.data || [];
                    if (!currentBucket.value && bucketList.value.length > 0) {
                        const compatibleBucket = bucketList.value.find((bucket) => bucket.compatible !== false);
                        currentBucket.value = compatibleBucket ? compatibleBucket.name : '';
                    }
                    if (currentBucket.value) {
                        currentFolder.value = currentFolder.value || '';
                        await loadFiles();
                    }
                }
            } catch (error) {
                console.error('Failed to load buckets:', error);
            }
        };

        const loadFiles = async () => {
            if (!currentBucket.value) {
                fileList.value = [];
                fileTotal.value = 0;
                return;
            }
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
                if (res.code !== 200) {
                    throw new Error(res.message || '加载失败');
                }
                if (res.data?.items !== undefined) {
                    fileList.value = ensureFolderFirst(res.data.items);
                    fileTotal.value = res.data.total || 0;
                } else {
                    fileList.value = ensureFolderFirst(res.data || []);
                    fileTotal.value = (res.data || []).length;
                }
                selectedFiles.value = [];
            } catch (error) {
                console.error('Failed to load files:', error);
            }
        };

        const refreshFiles = async () => {
            await loadFiles();
        };

        const onBucketChange = async () => {
            const targetBucket = bucketList.value.find((bucket) => bucket.name === currentBucket.value);
            if (targetBucket && targetBucket.compatible === false) {
                alert('当前存储桶名称不符合 Amazon S3 规范，已被禁用。');
                const compatibleBucket = bucketList.value.find((bucket) => bucket.compatible !== false);
                currentBucket.value = compatibleBucket ? compatibleBucket.name : '';
                if (!currentBucket.value) {
                    fileList.value = [];
                    fileTotal.value = 0;
                    return;
                }
            }
            currentFolder.value = '';
            filePage.value = 1;
            await loadFiles();
        };

        const navigateToFolder = async (folderPath) => {
            currentFolder.value = folderPath || '';
            filePage.value = 1;
            await loadFiles();
        };

        const navigateToBreadcrumb = async (item) => {
            currentFolder.value = item.path || '';
            filePage.value = 1;
            await loadFiles();
        };

        const goUp = async () => {
            if (!currentFolder.value) {
                return;
            }
            const normalized = currentFolder.value.endsWith('/')
                ? currentFolder.value.slice(0, -1)
                : currentFolder.value;
            const index = normalized.lastIndexOf('/');
            currentFolder.value = index >= 0 ? `${normalized.slice(0, index + 1)}` : '';
            filePage.value = 1;
            await loadFiles();
        };

        const toggleSelect = (file) => {
            const index = selectedFiles.value.indexOf(file.objectName);
            if (index === -1) {
                selectedFiles.value.push(file.objectName);
            } else {
                selectedFiles.value.splice(index, 1);
            }
        };

        const toggleSelectAll = (event) => {
            if (event.target.checked) {
                selectedFiles.value = fileList.value.map((file) => file.objectName);
            } else {
                selectedFiles.value = [];
            }
        };

        const prevPage = async () => {
            if (filePage.value > 1) {
                filePage.value -= 1;
                await loadFiles();
            }
        };

        const nextPage = async () => {
            if (filePage.value < totalPages.value) {
                filePage.value += 1;
                await loadFiles();
            }
        };

        const onPageSizeChange = async () => {
            filePage.value = 1;
            await loadFiles();
        };

        const handleFileSelect = (event) => {
            uploadQueue.value = Array.from(event.target.files || []);
        };

        const submitUpload = async () => {
            if (uploadQueue.value.length === 0) {
                return;
            }
            uploadProgress.value = 0;

            for (let index = 0; index < uploadQueue.value.length; index += 1) {
                const file = uploadQueue.value[index];
                const formData = new FormData();
                formData.append('file', file);
                formData.append('bucket', currentBucket.value);
                formData.append('folder', currentFolder.value);

                try {
                    await api.post('/file/upload', formData, {
                        headers: { 'Content-Type': 'multipart/form-data' },
                        onUploadProgress: (event) => {
                            if (event.total) {
                                uploadProgress.value = Math.round((event.loaded / event.total) * 100);
                            }
                        }
                    });
                } catch (error) {
                    alert(`上传失败: ${file.name}`);
                }
            }

            uploadQueue.value = [];
            uploadProgress.value = 0;
            showUploadDialog.value = false;
            await loadFiles();
        };

        const createFolder = async () => {
            if (!newFolderName.value) {
                return;
            }
            try {
                const path = `${currentFolder.value}${newFolderName.value}`;
                const res = await api.post('/file/folder', null, {
                    params: {
                        path,
                        bucket: currentBucket.value,
                        createTestFile: false
                    }
                });
                if (res.code === 200) {
                    newFolderName.value = '';
                    showFolderDialog.value = false;
                    await loadFiles();
                }
            } catch (error) {
                alert(error.response?.data?.message || '创建文件夹失败');
            }
        };

        const shareFile = (file) => {
            shareFileName.value = file.fileName;
            shareObjectName.value = file.objectName;
            shareExpireHours.value = 24;
            sharePassword.value = '';
            shareLink.value = '';
            showShareDialog.value = true;
        };

        const batchShare = () => {
            if (selectedFiles.value.length === 0) {
                return;
            }
            shareFileName.value = `已选择 ${selectedFiles.value.length} 项`;
            shareObjectName.value = selectedFiles.value.join(',');
            shareExpireHours.value = 24;
            sharePassword.value = '';
            shareLink.value = '';
            showShareDialog.value = true;
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
            } catch (error) {
                alert(error.response?.data?.message || '创建分享失败');
            }
        };

        const copyShareLink = async () => {
            if (!shareLink.value) {
                return;
            }
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
                alert('分享链接已复制');
            } catch (error) {
                alert('复制失败，请手动复制链接');
            }
        };

        const downloadFile = (file) => {
            window.open(`/api/file/download?objectName=${encodeURIComponent(file.objectName)}&bucket=${encodeURIComponent(currentBucket.value)}`, '_blank');
        };

        const batchDownload = () => {
            if (selectedFiles.value.length === 1) {
                const target = fileList.value.find((file) => file.objectName === selectedFiles.value[0]);
                if (target) {
                    downloadFile(target);
                }
                return;
            }
            if (selectedFiles.value.length > 1) {
                const objectNames = selectedFiles.value.map((name) => encodeURIComponent(name)).join(',');
                window.location.href = `/api/file/batch-download?objectNames=${objectNames}&bucket=${encodeURIComponent(currentBucket.value)}`;
            }
        };

        const deleteFile = async (file) => {
            if (!confirm(`确定删除 ${file.fileName} 吗？`)) {
                return;
            }
            try {
                await api.delete('/file/delete', { params: { objectName: file.objectName, bucket: currentBucket.value } });
                await loadFiles();
            } catch (error) {
                alert(error.response?.data?.message || '删除失败');
            }
        };

        const batchDelete = async () => {
            if (!selectedFiles.value.length || !confirm(`确定删除 ${selectedFiles.value.length} 项吗？`)) {
                return;
            }
            try {
                await api.delete('/file/batch-delete', {
                    params: {
                        objectNames: selectedFiles.value.join(','),
                        bucket: currentBucket.value
                    }
                });
                selectedFiles.value = [];
                await loadFiles();
            } catch (error) {
                alert(error.response?.data?.message || '删除失败');
            }
        };

        const previewFile = (file) => {
            window.open(`/api/file/preview?objectName=${encodeURIComponent(file.objectName)}&bucket=${encodeURIComponent(currentBucket.value)}`, '_blank');
        };

        const formatSize = (bytes) => {
            if (!bytes) return '0 B';
            if (bytes < 1024) return `${bytes} B`;
            if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
            if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
            return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
        };

        const formatDate = (dateStr) => {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            return date.toLocaleString('zh-CN');
        };

        onMounted(async () => {
            await loadRuntimeConfig();
            const token = localStorage.getItem('token');
            const user = localStorage.getItem('user');
            if (token && user) {
                userInfo.value = JSON.parse(user);
                isLoggedIn.value = true;
                await loadBuckets();
            }
        });

        return {
            isLoggedIn,
            loginForm,
            loginLoading,
            handleLogin,
            handleLogout,
            userInfo,
            bucketList,
            currentBucket,
            fileList,
            fileTotal,
            filePage,
            filePageSize,
            currentFolder,
            selectedFiles,
            showUploadDialog,
            showFolderDialog,
            showShareDialog,
            uploadQueue,
            uploadProgress,
            newFolderName,
            shareFileName,
            shareExpireHours,
            sharePassword,
            shareLink,
            showHttpsNotice,
            httpsSuggestionUrl,
            displayVersionLabel,
            totalPages,
            selectedCount,
            folderCount,
            currentFileCount,
            canGoUp,
            allSelected,
            currentPath,
            breadcrumbItems,
            currentViewTitle,
            currentViewSubtitle,
            selectionSummary,
            openFullMode,
            loadBuckets,
            loadFiles,
            refreshFiles,
            onBucketChange,
            navigateToFolder,
            navigateToBreadcrumb,
            goUp,
            toggleSelect,
            toggleSelectAll,
            prevPage,
            nextPage,
            onPageSizeChange,
            handleFileSelect,
            submitUpload,
            createFolder,
            shareFile,
            batchShare,
            createShare,
            copyShareLink,
            downloadFile,
            batchDownload,
            deleteFile,
            batchDelete,
            previewFile,
            formatSize,
            formatDate
        };
    }
}).mount('#app');
