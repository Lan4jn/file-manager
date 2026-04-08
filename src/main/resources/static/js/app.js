const { createApp, ref, computed, onMounted, onUnmounted, nextTick, watch } = Vue;
const permissionUiHelpers = window.PermissionUiHelpers || {
    shouldLoadRolesForUserDialog: (permissionCodes) => Array.isArray(permissionCodes)
        && permissionCodes.includes('API_ROLE_MANAGE'),
    canOpenRoleDialog: (permissionCodes) => Array.isArray(permissionCodes)
        && permissionCodes.includes('API_ROLE_MANAGE')
        && permissionCodes.includes('API_PERMISSION_READ'),
    shouldShowRoleBucketPermissions: (permissionCodes) => Array.isArray(permissionCodes)
        && (
            permissionCodes.includes('API_FILE_BROWSE')
            || permissionCodes.includes('API_FILE_OPERATE')
            || permissionCodes.includes('API_SHARE_MANAGE')
        )
};
const roleBucketPermissionHelpers = window.RoleBucketPermissionHelpers || {
    mergeRoleBucketPermissionRows: (rows, currentIndex) => ({
        rows: Array.isArray(rows) ? rows : [],
        merged: false,
        mergedIntoIndex: currentIndex
    }),
    shouldShowAllBucketsHint: (bucketName) => bucketName === '*'
};
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
const downloadHelpers = window.FileManagerDownloads || {};

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
        const lastCompatibleBucket = ref('');

        // 地址栏编辑相关
        const isPathEditing = ref(false);
        const editPathValue = ref('');
        const pathEditInputRef = ref(null);
        const runtimeConfig = ref({ httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' });
        const showHttpsNotice = computed(() => window.location.protocol === 'http:' && runtimeConfig.value.forceHttps);

        // Dialogs
        const showUploadDialog = ref(false);
        const uploadRef = ref(null);
        const uploading = ref(false);
        const showFolderDialog = ref(false);
        const showShareDialog = ref(false);
        const showBatchShareDialog = ref(false);
        const showUserDialog = ref(false);
        const showProfileDialog = ref(false);
        const showRoleDialog = ref(false);
        const showMinioKeyDialog = ref(false);
        const showHelpDialog = ref(false);
        const showVersionDialog = ref(false);
        const showPreviewDialog = ref(false);
        const downloadTasks = ref([]);
        const downloadCenterVisible = ref(false);
        const downloadCenterMinimized = ref(false);
        const helpContent = ref('');
        const versionList = ref([]);
        const selectedVersion = ref('');
        const versionContent = ref('');
        const previewLoading = ref(false);
        const previewTitle = ref('');
        const previewKind = ref('');
        const previewUrl = ref('');
        const previewText = ref('');
        let previewObjectUrl = '';

        // Form data
        const newFolderName = ref('');
        const createTestFile = ref(false);
        const shareForm = ref({
            objectName: '',
            bucket: '',
            expirationHours: 24,
            password: '',
            targetType: 'LINK',
            recipientUserIds: [],
            isFolder: false
        });
        const batchShareForm = ref({
            expirationHours: 24,
            password: '',
            targetType: 'LINK',
            recipientUserIds: []
        });
        const createEmptyUserForm = () => ({
            username: '',
            nickname: '',
            email: '',
            phone: '',
            enabled: true,
            roles: [],
            password: '',
            locked: false,
            unlockTime: null
        });
        const userForm = ref(createEmptyUserForm());
        const createEmptyProfileForm = () => ({
            nickname: '',
            email: '',
            phone: '',
            oldPassword: '',
            newPassword: '',
            confirmPassword: ''
        });
        const profileForm = ref(createEmptyProfileForm());
        const createEmptyRoleForm = () => ({
            id: null,
            code: '',
            name: '',
            description: '',
            menuPermissions: [],
            apiPermissions: [],
            bucketPermissions: []
        });
        const roleForm = ref(createEmptyRoleForm());
        const roleBucketEditIndex = ref(-1);
        const minioKeyForm = ref({
            name: '',
            bucketPermissions: [],  // 改为数组，每个元素包含 bucket, canRead, canWrite, canDelete
            expireDays: 0
        });
        const permissionInfo = ref({
            userId: null,
            username: '',
            superAdmin: false,
            roles: [],
            menuPermissions: [],
            apiPermissions: [],
            bucketPermissions: []
        });
        const permissionList = ref([]);
        const bucketActionOptions = [
            { label: '所有权限', value: '*' },
            { label: '查看文件', value: 'LIST_FILE' },
            { label: '上传文件', value: 'UPLOAD' },
            { label: '下载文件', value: 'DOWNLOAD' },
            { label: '删除文件', value: 'DELETE' },
            { label: '新建目录', value: 'CREATE_FOLDER' },
            { label: '分享文件', value: 'SHARE' },
            { label: '批量操作', value: 'BATCH' }
        ];
        const bucketActionLabelMap = Object.fromEntries(bucketActionOptions.map(item => [item.value, item.label]));
        const bucketSpecificActionValues = bucketActionOptions
            .filter(item => item.value !== '*')
            .map(item => item.value);

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
        const shareTab = ref('my-shares');
        const inboxList = ref([]);
        const inboxLoading = ref(false);
        const recipientList = ref([]);
        const recipientLoading = ref(false);
        const userList = ref([]);
        const roleList = ref([]);
        const minioKeyList = ref([]);
        const minioKeyLoading = ref(false);
        const auditLogList = ref([]);
        const auditLogLoading = ref(false);
        const auditLogPage = ref(1);
        const auditLogPageSize = ref(20);
        const auditLogTotal = ref(0);
        const auditLogQuery = ref({
            username: '',
            module: '',
            action: '',
            success: '',
            startTime: '',
            endTime: ''
        });

        // Loading states
        const shareLoading = ref(false);
        const userLoading = ref(false);
        const roleLoading = ref(false);

        // File config
        const fileConfig = ref({
            allowedExtensionsText: '',
            maxFileSizeMB: 100,
            frontendDownloadThresholdMB: 100
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
        const isSuperAdmin = computed(() => !!permissionInfo.value.superAdmin);
        const menuPermissionSet = computed(() => new Set(permissionInfo.value.menuPermissions || []));
        const apiPermissionSet = computed(() => new Set(permissionInfo.value.apiPermissions || []));
        const bucketPermissionMap = computed(() => {
            const map = new Map();
            (permissionInfo.value.bucketPermissions || []).forEach(item => {
                if (!item || !item.bucketName) {
                    return;
                }
                map.set(item.bucketName, new Set(item.actions || []));
            });
            return map;
        });
        const menuPermissionOptions = computed(() => {
            return (permissionList.value || [])
                .filter(item => item && item.code && item.code.startsWith('MENU_'))
                .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
        });
        const apiPermissionOptions = computed(() => {
            return (permissionList.value || [])
                .filter(item => item && item.code && item.code.startsWith('API_'))
                .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
        });
        const availableBucketOptions = computed(() => {
            return [
                { label: '所有存储桶', value: '*' },
                ...(bucketList.value || [])
                    .map(item => item.name)
                    .filter(Boolean)
                    .map(name => ({ label: name, value: name }))
            ];
        });
        const currentMenuAccessible = computed(() => {
            return canAccessMenu(currentMenu.value);
        });
        const shouldShowRoleBucketPermissionEditor = computed(() => {
            return permissionUiHelpers.shouldShowRoleBucketPermissions(roleForm.value.apiPermissions || []);
        });

        const currentViewTitle = computed(() => {
            const titles = {
                buckets: '存储空间',
                files: '我的文件',
                shares: '共享中心',
                'audit-logs': '审计日志',
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
                shares: '集中查看我的分享、站内收件箱与提取码信息。',
                'audit-logs': '审计文件操作、分享行为与关键系统动作。',
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
        const activeDownloadTaskCount = computed(() => downloadTasks.value.filter(task => task.status === 'running').length);
        const failedDownloadTaskCount = computed(() => downloadTasks.value.filter(task => task.status === 'failed').length);
        const primaryDownloadTask = computed(() => downloadTasks.value.find(task => task.status === 'running') || downloadTasks.value[0] || null);
        const previewHintGroups = [
            { label: '图片', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'] },
            { label: '文档', extensions: ['pdf', 'txt', 'md', 'log', 'csv'] },
            { label: '配置文件', extensions: ['yml', 'yaml', 'properties', 'xml', 'json'] },
            { label: '代码与脚本', extensions: ['java', 'js', 'ts', 'css', 'html', 'htm', 'sql', 'sh'] },
            { label: '音频', extensions: ['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac'] },
            { label: '视频', extensions: ['mp4', 'webm', 'ogg', 'mov', 'm4v'] }
        ];
        const previewSupportHint = computed(() => {
            return previewHintGroups
                .map(group => group.label + '（' + group.extensions.join(', ') + '）')
                .join('；');
        });
        const inboxUnreadCount = computed(() => {
            return inboxList.value.filter(item => !item.read).length;
        });
        const inboxUnreadSourceCount = computed(() => {
            const senderSet = new Set(
                inboxList.value
                    .filter(item => !item.read)
                    .map(item => item.senderUsername || '')
                    .filter(Boolean)
            );
            return senderSet.size;
        });
        const textPreviewMaxSize = 2 * 1024 * 1024;
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

        const createDownloadTask = (payload) => ({
            id: 'download-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
            type: payload.type || 'file',
            name: payload.name || '下载任务',
            status: 'running',
            step: payload.step || '准备中',
            progress: payload.progress || 0,
            downloadedBytes: payload.downloadedBytes || 0,
            totalBytes: payload.totalBytes || 0,
            speedBytesPerSecond: 0,
            error: ''
        });

        const addDownloadTask = (task) => {
            downloadTasks.value.unshift(task);
            openDownloadCenter();
            return task;
        };

        const updateDownloadTask = (taskId, patch) => {
            const index = downloadTasks.value.findIndex(task => task.id === taskId);
            if (index < 0) {
                return;
            }
            downloadTasks.value[index] = {
                ...downloadTasks.value[index],
                ...patch
            };
        };

        const completeDownloadTask = (taskId, patch = {}) => {
            updateDownloadTask(taskId, {
                status: 'completed',
                progress: 100,
                ...patch
            });
        };

        const failDownloadTask = (taskId, errorMessage) => {
            updateDownloadTask(taskId, {
                status: 'failed',
                error: errorMessage || '下载失败'
            });
            openDownloadCenter();
        };

        const buildAuthorizedHeaders = () => {
            if (!token.value) {
                return {};
            }
            return {
                Authorization: 'Bearer ' + token.value
            };
        };

        const toQueryUrl = (basePath, params) => {
            const url = new URL('/api' + basePath, window.location.origin);
            Object.entries(params || {}).forEach(([key, value]) => {
                if (value !== undefined && value !== null && value !== '') {
                    url.searchParams.set(key, value);
                }
            });
            return url.toString();
        };

        const shouldUseTaskCenterForFile = (file) => {
            if (!file || file.isFolder || !downloadHelpers.shouldUseChunkedDownload) {
                return false;
            }
            return downloadHelpers.shouldUseChunkedDownload(
                file.size || 0,
                fileConfig.value.frontendDownloadThresholdMB || 100
            );
        };

        const runChunkedFileDownload = async (file, urlBuilder) => {
            const task = addDownloadTask(createDownloadTask({
                type: 'single-file',
                name: file.fileName,
                step: '正在下载大文件',
                totalBytes: file.size || 0
            }));
            try {
                await downloadHelpers.downloadInChunks({
                    url: urlBuilder(),
                    fileName: file.fileName,
                    totalSize: file.size || 0,
                    headers: buildAuthorizedHeaders(),
                    chunkSize: downloadHelpers.DEFAULT_CHUNK_SIZE,
                    onProgress: (progress) => {
                        updateDownloadTask(task.id, {
                            step: '正在下载大文件',
                            progress: progress.progress,
                            downloadedBytes: progress.downloadedBytes,
                            totalBytes: progress.totalBytes,
                            speedBytesPerSecond: progress.speedBytesPerSecond
                        });
                    }
                });
                completeDownloadTask(task.id, {
                    step: '保存完成',
                    downloadedBytes: file.size || 0
                });
                ElementPlus.ElMessage.success('下载完成');
            } catch (error) {
                failDownloadTask(task.id, error.message || '下载失败');
                ElementPlus.ElMessage.error('下载失败: ' + (error.message || ''));
            }
        };

        const runBatchDownloadTask = async (objectNames, fileName) => {
            const task = addDownloadTask(createDownloadTask({
                type: 'batch-download',
                name: fileName,
                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[0] : '校验文件'
            }));
            try {
                updateDownloadTask(task.id, {
                    step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[1] : '请求服务端打包',
                    progress: 5
                });
                await downloadHelpers.downloadBlob({
                    fileName,
                    request: (onProgress) => api.get('/file/batch-download', {
                        params: { objectNames: objectNames.join(','), bucket: currentBucket.value },
                        responseType: 'blob',
                        timeout: 600000,
                        onDownloadProgress: (event) => {
                            updateDownloadTask(task.id, {
                                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[2] : '接收压缩包',
                                progress: event.total > 0 ? Math.min(95, (event.loaded / event.total) * 100) : 60
                            });
                            onProgress(event);
                        }
                    }),
                    onProgress: (progress) => {
                        updateDownloadTask(task.id, {
                            downloadedBytes: progress.downloadedBytes,
                            totalBytes: progress.totalBytes,
                            speedBytesPerSecond: progress.speedBytesPerSecond
                        });
                    }
                });
                updateDownloadTask(task.id, {
                    step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[3] : '保存文件',
                    progress: 98
                });
                completeDownloadTask(task.id, { step: '保存完成' });
                ElementPlus.ElMessage.success('下载完成');
            } catch (error) {
                const message = error.response?.data?.message || error.message || '下载失败';
                failDownloadTask(task.id, message);
                ElementPlus.ElMessage.error('下载失败: ' + message);
            }
        };

        const generateSharePassword = () => {
            return String(Math.floor(Math.random() * 10000)).padStart(4, '0');
        };

        const hasMenuPermission = (code) => {
            return isSuperAdmin.value || menuPermissionSet.value.has(code);
        };

        const hasApiPermission = (code) => {
            return isSuperAdmin.value || apiPermissionSet.value.has(code);
        };

        const hasBucketPermission = (bucketName, action) => {
            if (isSuperAdmin.value) {
                return true;
            }
            if (!bucketName) {
                return false;
            }
            const actions = bucketPermissionMap.value.get(bucketName);
            const globalActions = bucketPermissionMap.value.get('*');
            if (actions && actions.size > 0) {
                return actions.has(action) || actions.has('*');
            }
            return !!(globalActions && (globalActions.has(action) || globalActions.has('*')));
        };

        const canAccessMenu = (menuName) => {
            const menuCodeMap = {
                buckets: 'MENU_BUCKETS',
                files: 'MENU_FILES',
                shares: 'MENU_SHARES',
                'audit-logs': 'MENU_AUDIT_LOG',
                users: 'MENU_USERS',
                roles: 'MENU_ROLES',
                'minio-keys': 'MENU_MINIO_KEYS',
                config: 'MENU_CONFIG'
            };
            const code = menuCodeMap[menuName];
            return !code || hasMenuPermission(code);
        };

        const ensureMenuAccess = (menuName) => {
            if (canAccessMenu(menuName)) {
                return true;
            }
            ElementPlus.ElMessage.error('无权限访问该功能');
            return false;
        };

        const syncCurrentMenu = () => {
            const menuOrder = ['buckets', 'files', 'shares', 'audit-logs', 'users', 'roles', 'minio-keys', 'config'];
            if (canAccessMenu(currentMenu.value)) {
                return;
            }
            const nextMenu = menuOrder.find(item => canAccessMenu(item));
            currentMenu.value = nextMenu || 'files';
        };

        const loadCurrentPermissions = async () => {
            if (!token.value) {
                permissionInfo.value = {
                    userId: null,
                    username: '',
                    superAdmin: false,
                    roles: [],
                    menuPermissions: [],
                    apiPermissions: [],
                    bucketPermissions: []
                };
                return;
            }
            const res = await api.get('/auth/current-permissions');
            if (res.code === 200) {
                permissionInfo.value = res.data || {
                    userId: null,
                    username: '',
                    superAdmin: false,
                    roles: [],
                    menuPermissions: [],
                    apiPermissions: [],
                    bucketPermissions: []
                };
                syncCurrentMenu();
            }
        };

        const loadPermissions = async () => {
            if (!hasApiPermission('API_PERMISSION_READ')) {
                permissionList.value = [];
                return;
            }
            try {
                const res = await api.get('/admin/permissions');
                if (res.code === 200) {
                    permissionList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载权限列表失败');
            }
        };

        const addRoleBucketPermission = () => {
            roleForm.value.bucketPermissions.push({
                enabled: true,
                bucketName: currentBucket.value || '',
                actions: [],
                lastActions: []
            });
            roleBucketEditIndex.value = roleForm.value.bucketPermissions.length - 1;
        };

        const removeRoleBucketPermission = (index) => {
            roleForm.value.bucketPermissions.splice(index, 1);
            if (roleBucketEditIndex.value === index) {
                roleBucketEditIndex.value = -1;
            } else if (roleBucketEditIndex.value > index) {
                roleBucketEditIndex.value -= 1;
            }
        };

        const finishEditRoleBucketPermission = () => {
            if (roleBucketEditIndex.value < 0) {
                return;
            }
            normalizeRoleBucketActions(roleForm.value.bucketPermissions[roleBucketEditIndex.value]);
            roleBucketEditIndex.value = -1;
        };

        const handleRoleBucketNameChange = (index, row) => {
            if (!row) {
                return;
            }
            const mergeResult = roleBucketPermissionHelpers.mergeRoleBucketPermissionRows(roleForm.value.bucketPermissions, index);
            if (mergeResult.merged) {
                roleForm.value.bucketPermissions = mergeResult.rows;
                roleForm.value.bucketPermissions.forEach(normalizeRoleBucketActions);
                roleBucketEditIndex.value = mergeResult.mergedIntoIndex;
                ElementPlus.ElMessage.info('该存储桶已存在，已合并到原配置项');
                return;
            }
            if (roleBucketPermissionHelpers.shouldShowAllBucketsHint(row.bucketName)) {
                ElementPlus.ElMessage.info('已配置所有存储桶，当前用户将默认显示全部存储桶');
            }
            roleBucketEditIndex.value = index;
        };

        const isAllBucketActionsSelected = (actions) => {
            if (!Array.isArray(actions)) {
                return false;
            }
            const uniqueActions = [...new Set(actions.filter(Boolean))];
            const includesAllSpecificActions = bucketSpecificActionValues.every(action => uniqueActions.includes(action));
            return uniqueActions.includes('*') || includesAllSpecificActions;
        };

        const getExpandedRoleBucketActions = (actions) => {
            if (!Array.isArray(actions)) {
                return [];
            }
            const uniqueActions = [...new Set(actions.filter(Boolean))];
            if (isAllBucketActionsSelected(uniqueActions)) {
                return ['*', ...bucketSpecificActionValues];
            }
            return uniqueActions.filter(action => action !== '*');
        };

        const getStoredRoleBucketActions = (actions) => {
            return isAllBucketActionsSelected(actions) ? ['*'] : getExpandedRoleBucketActions(actions);
        };

        const handleRoleBucketActionsChange = (actions, row) => {
            if (!row) {
                return;
            }
            const uniqueActions = [...new Set((actions || []).filter(Boolean))];
            const previousActions = Array.isArray(row.lastActions) ? row.lastActions : [];
            const hadAllActions = isAllBucketActionsSelected(previousActions);
            const hasAllSpecificActions = bucketSpecificActionValues.every(action => uniqueActions.includes(action));

            if (hadAllActions && !uniqueActions.includes('*') && hasAllSpecificActions) {
                row.actions = [];
            } else if (hadAllActions && uniqueActions.includes('*') && !hasAllSpecificActions) {
                row.actions = uniqueActions.filter(action => action !== '*');
            } else if (uniqueActions.includes('*') || hasAllSpecificActions) {
                row.actions = ['*', ...bucketSpecificActionValues];
            } else {
                row.actions = uniqueActions.filter(action => action !== '*');
            }
            row.lastActions = [...row.actions];
        };

        const normalizeRoleBucketActions = (row) => {
            if (!row) {
                return;
            }
            row.actions = getExpandedRoleBucketActions(row.actions);
            row.lastActions = [...row.actions];
        };

        const getRoleBucketActionDisplayText = (actions) => {
            if (!actions || !actions.length) {
                return '';
            }
            if (isAllBucketActionsSelected(actions)) {
                return bucketActionLabelMap['*'];
            }
            return actions
                .filter(action => action !== '*')
                .map(action => bucketActionLabelMap[action] || action)
                .join('、');
        };

        const formatBucketPermissionBuckets = (bucketName) => {
            if (!bucketName) {
                return '未选择';
            }
            return bucketName === '*' ? '所有存储桶' : bucketName;
        };

        const formatBucketPermissionActions = (actions) => {
            if (!actions || !actions.length) {
                return '未选择';
            }
            return getRoleBucketActionDisplayText(actions) || '未选择';
        };

        const uploadUrl = computed(() => '/api/file/upload');
        const uploadHeaders = computed(() => ({
            Authorization: 'Bearer ' + token.value
        }));
        const uploadData = computed(() => ({
            folder: currentFolder.value,
            bucket: currentBucket.value
        }));

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
                console.log('[Login] Response received:', res);
                if (res.code === 200) {
                    token.value = res.data.token;
                    localStorage.setItem('token', token.value);
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    await loadCurrentPermissions();
                    await loadPermissions();
                    await loadInbox();
                    handleMenuSelect(currentMenu.value);
                } else {
                    ElementPlus.ElMessage.error(res.message || '登录失败');
                }
            } catch (error) {
                console.error('[Login] Error:', error);
                ElementPlus.ElMessage.error(loginErrorHelpers.extractLoginErrorMessage(error));
            } finally {
                loginLoading.value = false;
            }
        };

        const logout = () => {
            token.value = '';
            localStorage.removeItem('token');
            userInfo.value = {};
            permissionInfo.value = {
                userId: null,
                username: '',
                superAdmin: false,
                roles: [],
                menuPermissions: [],
                apiPermissions: [],
                bucketPermissions: []
            };
            permissionList.value = [];
            inboxList.value = [];
            recipientList.value = [];
            isLoggedIn.value = false;
            currentMenu.value = 'buckets';
            shareTab.value = 'my-shares';
            loginForm.value = { username: '', password: '' };
        };

        const handleCommand = (command) => {
            if (command === 'logout') {
                logout();
            } else if (command === 'profile') {
                profileForm.value = {
                    nickname: userInfo.value.nickname || '',
                    email: userInfo.value.email || '',
                    phone: userInfo.value.phone || '',
                    oldPassword: '',
                    newPassword: '',
                    confirmPassword: ''
                };
                showProfileDialog.value = true;
            }
        };

        const saveProfile = async () => {
            try {
                const profilePayload = {
                    nickname: (profileForm.value.nickname || '').trim(),
                    email: (profileForm.value.email || '').trim(),
                    phone: (profileForm.value.phone || '').trim()
                };
                const profileRes = await api.put('/auth/profile', profilePayload);
                if (profileRes.code === 200) {
                    userInfo.value = {
                        ...userInfo.value,
                        nickname: profileRes.data.nickname,
                        email: profileRes.data.email,
                        phone: profileRes.data.phone
                    };
                }

                if (profileForm.value.oldPassword || profileForm.value.newPassword || profileForm.value.confirmPassword) {
                    if (!profileForm.value.oldPassword || !profileForm.value.newPassword) {
                        ElementPlus.ElMessage.error('修改密码需要填写旧密码和新密码');
                        return;
                    }
                    if (profileForm.value.newPassword !== profileForm.value.confirmPassword) {
                        ElementPlus.ElMessage.error('两次输入的新密码不一致');
                        return;
                    }
                    await api.post('/auth/change-password', {
                        oldPassword: profileForm.value.oldPassword,
                        newPassword: profileForm.value.newPassword
                    });
                }

                ElementPlus.ElMessage.success('个人信息已保存');
                showProfileDialog.value = false;
                profileForm.value = createEmptyProfileForm();
            } catch (error) {
                ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '保存失败');
            }
        };

        const checkAuth = async () => {
            if (!token.value) return;
            try {
                const res = await api.get('/auth/info');
                if (res.code === 200) {
                    userInfo.value = res.data;
                    isLoggedIn.value = true;
                    await loadCurrentPermissions();
                    await loadPermissions();
                    await loadInbox();
                    handleMenuSelect(currentMenu.value);
                } else {
                    logout();
                }
            } catch (error) {
                logout();
            }
        };

        const handleMenuSelect = (index) => {
            if (!ensureMenuAccess(index)) {
                return;
            }
            currentMenu.value = index;
            if (index === 'buckets') {
                loadBuckets();
            } else if (index === 'files') {
                if (!currentBucket.value && bucketList.value.length > 0) {
                    const compatibleBucket = bucketList.value.find(item => item.compatible !== false);
                    if (!compatibleBucket) {
                        ElementPlus.ElMessage.warning('当前没有可用的兼容存储桶');
                        return;
                    }
                    currentBucket.value = compatibleBucket.name;
                    lastCompatibleBucket.value = compatibleBucket.name;
                }
                const targetBucket = bucketList.value.find(item => item.name === currentBucket.value);
                if (targetBucket && targetBucket.compatible === false) {
                    showBucketCompatibilityWarning(targetBucket.name);
                    const compatibleBucket = bucketList.value.find(item => item.compatible !== false);
                    currentBucket.value = compatibleBucket ? compatibleBucket.name : '';
                    lastCompatibleBucket.value = currentBucket.value || '';
                    if (!currentBucket.value) {
                        return;
                    }
                }
                loadFiles();
            } else if (index === 'shares') {
                loadShares();
                loadInbox();
            } else if (index === 'audit-logs') {
                loadAuditLogs();
            } else if (index === 'users') {
                loadUsers();
                if (permissionUiHelpers.shouldLoadRolesForUserDialog(permissionInfo.value.apiPermissions || [])) {
                    loadRoles();
                }
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

        const showBucketCompatibilityWarning = (bucketName) => {
            ElementPlus.ElMessageBox.alert(
                '当前存储桶 "' + bucketName + '" 的名称不符合 Amazon S3 规范，已被禁用，无法进入文件管理。\n\n' +
                '桶名规范：\n' +
                '1. 长度为 3 到 63 个字符\n' +
                '2. 只能使用小写字母、数字、点(.)、连字符(-)\n' +
                '3. 必须以字母或数字开头和结尾\n' +
                '4. 不能包含连续的点(..)\n' +
                '5. 不能使用 IP 地址格式',
                '桶名不规范',
                { confirmButtonText: '我知道了', type: 'warning' }
            );
        };

        const selectBucket = (bucket) => {
            const targetBucket = typeof bucket === 'string'
                ? bucketList.value.find(item => item.name === bucket) || { name: bucket, compatible: true }
                : bucket;
            if (targetBucket && targetBucket.compatible === false) {
                showBucketCompatibilityWarning(targetBucket.name);
                return;
            }
            currentBucket.value = targetBucket.name;
            lastCompatibleBucket.value = targetBucket.name;
            currentFolder.value = '';
            currentMenu.value = 'files';
            updateBreadcrumb();
            loadFiles();
        };

        const onBucketSelect = () => {
            const targetBucket = bucketList.value.find(item => item.name === currentBucket.value);
            if (targetBucket && targetBucket.compatible === false) {
                showBucketCompatibilityWarning(targetBucket.name);
                currentBucket.value = lastCompatibleBucket.value || '';
                return;
            }
            lastCompatibleBucket.value = currentBucket.value || '';
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
            updateBreadcrumb();
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
                        fileList.value = ensureFolderFirst(res.data.items);
                        fileTotal.value = res.data.total || 0;
                    } else {
                        // 非分页模式（兼容旧版）
                        fileList.value = ensureFolderFirst(res.data);
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

        const loadUploadConfig = async () => {
            try {
                const res = await api.get('/file/upload-config');
                if (res.code === 200) {
                    const extensions = res.data.allowedExtensions || [];
                    fileConfig.value.allowedExtensionsText = extensions.join(',');
                    if (res.data.maxFileSize === -1) {
                        fileConfig.value.maxFileSizeMB = -1;
                    } else {
                        fileConfig.value.maxFileSizeMB = Math.floor((res.data.maxFileSize || 104857600) / 1024 / 1024);
                    }
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载上传配置失败');
            }
        };

        // 打开上传对话框时加载配置
        const openUploadDialog = () => {
            loadUploadConfig();
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
                const pathInput = pathEditInputRef.value;
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
            if (shouldUseTaskCenterForFile(file) && downloadHelpers.downloadInChunks) {
                await runChunkedFileDownload(file, () => toQueryUrl('/file/download', {
                    objectName: file.objectName,
                    bucket: currentBucket.value
                }));
                return;
            }
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

        const imagePreviewExtensions = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'];
        const textPreviewExtensions = ['txt', 'md', 'log', 'json', 'xml', 'yml', 'yaml', 'properties', 'csv', 'java', 'js', 'ts', 'css', 'html', 'htm', 'sql', 'sh'];
        const audioPreviewExtensions = ['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac'];
        const videoPreviewExtensions = ['mp4', 'webm', 'ogg', 'mov', 'm4v'];

        const getFileExtension = (fileName) => {
            if (!fileName || fileName.indexOf('.') === -1) {
                return '';
            }
            return fileName.split('.').pop().toLowerCase();
        };

        const resolvePreviewKind = (file) => {
            const extension = getFileExtension(file.fileName);
            const contentType = (file.contentType || '').toLowerCase();

            if (contentType.startsWith('image/') || imagePreviewExtensions.includes(extension)) {
                return 'image';
            }
            if (contentType === 'application/pdf' || extension === 'pdf') {
                return 'pdf';
            }
            if (contentType.startsWith('text/') || textPreviewExtensions.includes(extension)) {
                return 'text';
            }
            if (contentType.startsWith('audio/') || audioPreviewExtensions.includes(extension)) {
                return 'audio';
            }
            if (contentType.startsWith('video/') || videoPreviewExtensions.includes(extension)) {
                return 'video';
            }
            return 'unsupported';
        };

        const resetPreviewContent = () => {
            previewTitle.value = '';
            previewKind.value = '';
            previewText.value = '';
            if (previewObjectUrl) {
                window.URL.revokeObjectURL(previewObjectUrl);
                previewObjectUrl = '';
            }
            previewUrl.value = '';
        };

        const closePreviewDialog = () => {
            previewLoading.value = false;
            resetPreviewContent();
        };

        const previewFile = async (file) => {
            const kind = resolvePreviewKind(file);
            previewTitle.value = file.fileName || '文件预览';

            if (kind === 'unsupported') {
                previewKind.value = 'unsupported';
                previewText.value = '';
                previewUrl.value = '';
                showPreviewDialog.value = true;
                ElementPlus.ElMessage.warning('当前文件类型不支持在线预览');
                return;
            }

            resetPreviewContent();
            previewTitle.value = file.fileName || '文件预览';
            previewKind.value = kind;
            previewLoading.value = true;
            showPreviewDialog.value = true;

            try {
                const response = await api.get('/file/preview', {
                    params: { objectName: file.objectName, bucket: currentBucket.value },
                    responseType: 'blob'
                });
                const contentType = response.headers?.['content-type'] || file.contentType || 'application/octet-stream';
                const blob = response.data instanceof Blob
                    ? response.data
                    : new Blob([response.data], { type: contentType });
                if (kind === 'text') {
                    if (blob.size > textPreviewMaxSize) {
                        throw new Error('文本文件过大，请下载后查看');
                    }
                    previewText.value = await blob.text();
                    return;
                }
                previewObjectUrl = window.URL.createObjectURL(blob);
                previewUrl.value = previewObjectUrl;
            } catch (error) {
                showPreviewDialog.value = false;
                ElementPlus.ElMessage.error('预览失败: ' + (error.response?.data?.message || error.message || '未知错误'));
            } finally {
                previewLoading.value = false;
            }
        };

        const buildShareUrl = (share, includePassword = false) => {
            const url = new URL('/share/' + share.shareCode, window.location.origin);
            if (includePassword && share.hasPassword && share.password) {
                url.searchParams.set('password', share.password);
            }
            return url.toString();
        };

        const buildShareCopyText = (share, mode = 'notice') => {
            const baseUrl = buildShareUrl(share, mode === 'with-password');
            if (!share.hasPassword || !share.password) {
                return baseUrl;
            }
            if (mode === 'with-password') {
                return baseUrl;
            }
            return baseUrl + ' （分享已加密，提取码：' + share.password + '）';
        };

        const formatShareTargetType = (share) => {
            return share.targetType === 'USER' ? '分享到用户' : '分享到链接';
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
                password: generateSharePassword(),
                targetType: 'LINK',
                recipientUserIds: []
            };
            showBatchShareDialog.value = true;
        };

        const handleBatchShareTargetTypeChange = async (targetType) => {
            if (targetType === 'USER') {
                batchShareForm.value.password = '';
                await loadRecipients();
            } else if (!batchShareForm.value.password) {
                batchShareForm.value.password = generateSharePassword();
                batchShareForm.value.recipientUserIds = [];
            }
        };

        const createBatchShare = async () => {
            try {
                if (batchShareForm.value.targetType === 'USER'
                    && (!batchShareForm.value.recipientUserIds || batchShareForm.value.recipientUserIds.length === 0)) {
                    ElementPlus.ElMessage.warning('请选择至少一个接收用户');
                    return;
                }
                const objectNames = selectedFiles.value.map(f => f.objectName);
                const res = await api.post('/share/batch-create', {
                    objectNames: objectNames,
                    bucket: currentBucket.value,
                    expirationHours: batchShareForm.value.expirationHours,
                    password: batchShareForm.value.targetType === 'USER' ? '' : batchShareForm.value.password,
                    targetType: batchShareForm.value.targetType,
                    recipientUserIds: batchShareForm.value.targetType === 'USER' ? batchShareForm.value.recipientUserIds : []
                });
                if (res.code === 200) {
                    if (batchShareForm.value.targetType === 'USER') {
                        ElementPlus.ElMessage.success('批量站内分享已发送给指定用户');
                    } else {
                        ElementPlus.ElMessage.success('分享创建成功');
                        try {
                            await copyText(buildShareCopyText(res.data, 'notice'));
                            ElementPlus.ElMessage.info('分享链接说明已复制到剪贴板');
                        } catch (copyError) {
                            ElementPlus.ElMessage.warning('分享已创建，但自动复制失败：' + copyError.message);
                        }
                    }
                    showBatchShareDialog.value = false;
                    await loadInbox();
                    loadShares();
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
            await runBatchDownloadTask(
                selectedFiles.value.map(f => f.objectName),
                '批量下载_' + selectedFiles.value.length + '个文件.zip'
            );
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
                expirationHours: shareConfig.value.expireHours || 24,
                password: '',
                targetType: 'LINK',
                recipientUserIds: [],
                isFolder: file.isFolder || false
            };
            shareForm.value.password = generateSharePassword();
            showShareDialog.value = true;
        };

        const handleShareTargetTypeChange = async (targetType) => {
            if (targetType === 'USER') {
                shareForm.value.password = '';
                await loadRecipients();
            } else {
                shareForm.value.recipientUserIds = [];
                if (!shareForm.value.password) {
                    shareForm.value.password = generateSharePassword();
                }
            }
        };

        const createShare = async () => {
            try {
                if (shareForm.value.targetType === 'USER' && (!shareForm.value.recipientUserIds || shareForm.value.recipientUserIds.length === 0)) {
                    ElementPlus.ElMessage.warning('请选择至少一个接收用户');
                    return;
                }
                const res = await api.post('/share/create', {
                    objectName: shareForm.value.objectName,
                    bucket: shareForm.value.bucket,
                    expirationHours: shareForm.value.expirationHours,
                    password: shareForm.value.targetType === 'USER' ? '' : shareForm.value.password,
                    targetType: shareForm.value.targetType,
                    recipientUserIds: shareForm.value.targetType === 'USER' ? shareForm.value.recipientUserIds : []
                });
                if (res.code === 200) {
                    if (shareForm.value.targetType === 'USER') {
                        ElementPlus.ElMessage.success('站内分享已发送给指定用户');
                    } else {
                        let successMsg = '分享创建成功';
                        if (shareForm.value.isFolder) {
                            successMsg += '（支持在线浏览当前目录内容）';
                        }
                        ElementPlus.ElMessage.success(successMsg);
                        try {
                            await copyText(buildShareCopyText(res.data, 'notice'));
                            ElementPlus.ElMessage.info('分享链接说明已复制到剪贴板');
                        } catch (copyError) {
                            ElementPlus.ElMessage.warning('分享已创建，但自动复制失败：' + copyError.message);
                        }
                    }
                    showShareDialog.value = false;
                    await loadInbox();
                    loadShares();
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
                        hasPasswordText: item.targetType === 'USER'
                            ? '站内信'
                            : (item.hasPassword && item.password ? '提取码：' + item.password : '公开访问'),
                        shareTargetTypeText: formatShareTargetType(item),
                        shareUrl: buildShareUrl(item, false),
                        shareUrlWithPassword: buildShareUrl(item, true),
                        shareNoticeText: buildShareCopyText(item, 'notice'),
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

        const copyShareLink = (share, mode = 'notice') => {
            const content = buildShareCopyText(share, mode);
            copyText(content)
                .then(() => {
                    ElementPlus.ElMessage.success('链接已复制到剪贴板');
                })
                .catch((error) => {
                    ElementPlus.ElMessage.error('复制失败: ' + error.message);
                });
        };

        const loadRecipients = async () => {
            recipientLoading.value = true;
            try {
                const res = await api.get('/messages/recipients');
                if (res.code === 200) {
                    recipientList.value = res.data || [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载接收用户失败');
            } finally {
                recipientLoading.value = false;
            }
        };

        const loadInbox = async () => {
            inboxLoading.value = true;
            try {
                const res = await api.get('/messages/inbox', {
                    params: { page: 1, size: 100 }
                });
                if (res.code === 200) {
                    inboxList.value = (res.data.items || []).map(item => ({
                        ...item,
                        createTimeText: formatDateTime(item.createTime),
                        readTimeText: formatDateTime(item.readTime)
                    }));
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载收件箱失败');
            } finally {
                inboxLoading.value = false;
            }
        };

        const openInboxShare = async (message) => {
            try {
                const res = await api.get('/messages/' + message.id + '/share');
                if (res.code === 200 && res.data?.shareCode) {
                    window.open(buildShareUrl(res.data, !!res.data.password), '_blank');
                    await loadInbox();
                }
            } catch (error) {
                ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '打开分享失败');
            }
        };

        const markInboxRead = async (message) => {
            try {
                const res = await api.post('/messages/' + message.id + '/read');
                if (res.code === 200) {
                    await loadInbox();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('标记已读失败');
            }
        };

        const deleteInboxMessage = async (message) => {
            try {
                await api.delete('/messages/' + message.id);
                ElementPlus.ElMessage.success('删除成功');
                await loadInbox();
            } catch (error) {
                ElementPlus.ElMessage.error('删除站内信失败');
            }
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

        const loadAuditLogs = async () => {
            auditLogLoading.value = true;
            try {
                const params = {
                    page: auditLogPage.value,
                    size: auditLogPageSize.value
                };
                if (auditLogQuery.value.username) {
                    params.username = auditLogQuery.value.username;
                }
                if (auditLogQuery.value.module) {
                    params.module = auditLogQuery.value.module;
                }
                if (auditLogQuery.value.action) {
                    params.action = auditLogQuery.value.action;
                }
                if (auditLogQuery.value.success !== '') {
                    params.success = auditLogQuery.value.success;
                }
                if (auditLogQuery.value.startTime) {
                    params.startTime = auditLogQuery.value.startTime;
                }
                if (auditLogQuery.value.endTime) {
                    params.endTime = auditLogQuery.value.endTime;
                }
                const res = await api.get('/audit-logs', { params });
                if (res.code === 200) {
                    auditLogList.value = (res.data.items || []).map(item => ({
                        ...item,
                        createTimeText: formatDateTime(item.createTime)
                    }));
                    auditLogTotal.value = res.data.total || 0;
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载审计日志失败');
            } finally {
                auditLogLoading.value = false;
            }
        };

        const resetAuditLogQuery = () => {
            auditLogQuery.value = {
                username: '',
                module: '',
                action: '',
                success: '',
                startTime: '',
                endTime: ''
            };
            auditLogPage.value = 1;
            loadAuditLogs();
        };

        const handleAuditLogPageChange = (page) => {
            auditLogPage.value = page;
            loadAuditLogs();
        };

        const handleAuditLogSizeChange = (size) => {
            auditLogPageSize.value = size;
            auditLogPage.value = 1;
            loadAuditLogs();
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

        const openCreateUserDialog = async () => {
            if (permissionUiHelpers.shouldLoadRolesForUserDialog(permissionInfo.value.apiPermissions || [])) {
                await loadRoles();
            }
            userForm.value = createEmptyUserForm();
            showUserDialog.value = true;
        };

        const editUser = async (user) => {
            if (permissionUiHelpers.shouldLoadRolesForUserDialog(permissionInfo.value.apiPermissions || [])) {
                await loadRoles();
            }
            userForm.value = { ...user, roles: user.roles || [], password: '' };
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
                    userForm.value = createEmptyUserForm();
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

        const unlockUser = async (user) => {
            try {
                await ElementPlus.ElMessageBox.confirm(
                    '确定要手动解封用户 "' + user.username + '" 吗？',
                    '解封用户',
                    {
                        confirmButtonText: '确定解封',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }
                );
                const res = await api.post('/admin/users/' + user.id + '/unlock');
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('解封成功');
                    await loadUsers();
                    if (userForm.value.id === user.id) {
                        userForm.value.locked = false;
                        userForm.value.unlockTime = null;
                    }
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '解封失败');
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

        const openCreateRoleDialog = async () => {
            if (!permissionUiHelpers.canOpenRoleDialog(permissionInfo.value.apiPermissions || [])) {
                ElementPlus.ElMessage.error('缺少权限读取权限，无法编辑角色授权');
                return;
            }
            roleForm.value = createEmptyRoleForm();
            roleBucketEditIndex.value = -1;
            await loadPermissions();
            showRoleDialog.value = true;
        };

        const editRole = async (role) => {
            if (!permissionUiHelpers.canOpenRoleDialog(permissionInfo.value.apiPermissions || [])) {
                ElementPlus.ElMessage.error('缺少权限读取权限，无法编辑角色授权');
                return;
            }
            await loadPermissions();
            const detailRes = await api.get('/admin/roles/' + role.id + '/permissions-detail');
            const detail = detailRes.data || {};
            roleForm.value = {
                id: role.id,
                code: role.code || '',
                name: role.name || '',
                description: role.description || '',
                menuPermissions: detail.menuPermissions || [],
                apiPermissions: detail.apiPermissions || [],
                bucketPermissions: (detail.bucketPermissions || []).map(item => ({
                    enabled: true,
                    bucketName: item.bucketName || '',
                    actions: Array.isArray(item.actions) ? [...item.actions] : []
                }))
            };
            roleForm.value.bucketPermissions.forEach(normalizeRoleBucketActions);
            roleBucketEditIndex.value = -1;
            showRoleDialog.value = true;
        };

        const saveRole = async () => {
            const payload = {
                code: (roleForm.value.code || '').trim(),
                name: (roleForm.value.name || '').trim(),
                description: (roleForm.value.description || '').trim()
            };
            const permissionPayload = {
                menuPermissions: roleForm.value.menuPermissions || [],
                apiPermissions: roleForm.value.apiPermissions || [],
                bucketPermissions: (roleForm.value.bucketPermissions || [])
                    .filter(item => item.enabled)
                    .filter(item => item.bucketName)
                    .map(item => ({
                        bucketName: item.bucketName,
                        actions: Array.isArray(item.actions)
                            ? (() => {
                                const normalized = { actions: [...item.actions] };
                                return getStoredRoleBucketActions(normalized.actions);
                            })()
                            : []
                    }))
            };
            try {
                const roleRes = roleForm.value.id
                    ? await api.put('/admin/roles/' + roleForm.value.id, payload)
                    : await api.post('/admin/roles', payload);
                const savedRole = roleRes.data;
                await api.post('/admin/roles/' + savedRole.id + '/permissions-detail', permissionPayload);
                ElementPlus.ElMessage.success('保存成功');
                showRoleDialog.value = false;
                roleForm.value = createEmptyRoleForm();
                roleBucketEditIndex.value = -1;
                await loadRoles();
            } catch (error) {
                ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '保存失败');
            }
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
                    fileConfig.value.frontendDownloadThresholdMB = res.data.frontendDownloadThresholdMB || 100;
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
                    maxFileSize: fileConfig.value.maxFileSizeMB * 1024 * 1024,
                    frontendDownloadThresholdMB: fileConfig.value.frontendDownloadThresholdMB
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

        const openDownloadCenter = () => {
            downloadCenterVisible.value = true;
            downloadCenterMinimized.value = false;
        };

        const minimizeDownloadCenter = () => {
            downloadCenterVisible.value = false;
            downloadCenterMinimized.value = true;
        };

        const restoreDownloadCenter = () => {
            downloadCenterVisible.value = true;
            downloadCenterMinimized.value = false;
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
                        const currentVersion = runtimeConfig.value.appVersion
                            ? `v${runtimeConfig.value.appVersion}`
                            : '';
                        const preferredVersion = versionList.value.find(item => item.version === currentVersion);
                        loadVersionNote(preferredVersion || versionList.value[0]);
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
            const pathInput = pathEditInputRef.value;
            if (isPathEditing.value && pathStrip && pathInput) {
                if (!pathStrip.contains(e.target) && e.target !== pathInput) {
                    cancelPathEdit();
                }
            }
        };

        const loadRuntimeConfig = async () => {
            try {
                const res = await api.get('/auth/runtime-config');
                if (res.code === 200) {
                    runtimeConfig.value = res.data || { httpPort: null, httpsPort: null, forceHttps: false, appVersion: '' };
                }
            } catch (error) {
                // Ignore and fallback to current URL scheme replacement.
            }
        };

        const handleDocumentKeydown = (e) => {
            const isPathShortcut = (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'l';
            if (!isPathShortcut || currentMenu.value !== 'files') {
                return;
            }
            e.preventDefault();
            startPathEdit();
        };

        // Lifecycle
        onMounted(() => {
            loadRuntimeConfig();
            checkAuth();
            document.addEventListener('click', handleDocumentClick);
            document.addEventListener('keydown', handleDocumentKeydown);
        });

        onUnmounted(() => {
            document.removeEventListener('click', handleDocumentClick);
            document.removeEventListener('keydown', handleDocumentKeydown);
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
            showProfileDialog,
            showRoleDialog,
            showMinioKeyDialog,
            showHelpDialog,
            showVersionDialog,
            showPreviewDialog,
            downloadTasks,
            downloadCenterVisible,
            downloadCenterMinimized,
            helpContent,
            versionList,
            selectedVersion,
            versionContent,
            loadVersionNote,
            previewLoading,
            previewTitle,
            previewKind,
            previewUrl,
            previewText,
            previewHintGroups,
            previewSupportHint,
            closePreviewDialog,
            minioKeyForm,
            minioKeyList,
            minioKeyLoading,
            addBucketPermission,
            removeBucketPermission,
            newFolderName,
            createTestFile,
            shareForm,
            batchShareForm,
            shareTab,
            inboxList,
            inboxLoading,
            inboxUnreadCount,
            inboxUnreadSourceCount,
            recipientList,
            recipientLoading,
            userForm,
            profileForm,
            roleForm,
            shareList,
            auditLogList,
            auditLogLoading,
            auditLogPage,
            auditLogPageSize,
            auditLogTotal,
            auditLogQuery,
            userList,
            roleList,
            shareLoading,
            userLoading,
            roleLoading,
            fileConfig,
            shareConfig,
            permissionInfo,
            permissionList,
            isAdmin,
            isSuperAdmin,
            menuPermissionOptions,
            apiPermissionOptions,
            availableBucketOptions,
            bucketActionOptions,
            currentMenuAccessible,
            shouldShowRoleBucketPermissionEditor,
            currentViewTitle,
            currentViewDescription,
            bucketCount,
            folderCount,
            currentFileCount,
            currentPageSize,
            activeDownloadTaskCount,
            failedDownloadTaskCount,
            primaryDownloadTask,
            showHttpsNotice,
            httpsSuggestionUrl,
            displayVersionLabel,
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
            pathEditInputRef,
            startPathEdit,
            confirmPathEdit,
            cancelPathEdit,
            uploadUrl,
            uploadHeaders,
            uploadData,
            handleLogin,
            logout,
            handleCommand,
            saveProfile,
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
            handleShareTargetTypeChange,
            handleBatchShareTargetTypeChange,
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
            loadInbox,
            openInboxShare,
            markInboxRead,
            deleteInboxMessage,
            loadAuditLogs,
            resetAuditLogQuery,
            handleAuditLogPageChange,
            handleAuditLogSizeChange,
            openCreateUserDialog,
            editUser,
            saveUser,
            deleteUser,
            unlockUser,
            openCreateRoleDialog,
            editRole,
            saveRole,
            deleteRole,
            addRoleBucketPermission,
            removeRoleBucketPermission,
            roleBucketEditIndex,
            finishEditRoleBucketPermission,
            handleRoleBucketActionsChange,
            getRoleBucketActionDisplayText,
            handleRoleBucketNameChange,
            formatBucketPermissionBuckets,
            formatBucketPermissionActions,
            canAccessMenu,
            hasMenuPermission,
            hasApiPermission,
            hasBucketPermission,
            loadMinioKeys,
            createMinioKey,
            copyMinioKey,
            deleteMinioKey,
            loadFileConfig,
            saveFileConfig,
            loadShareConfig,
            saveShareConfig,
            openDownloadCenter,
            minimizeDownloadCenter,
            restoreDownloadCenter,
            formatSize,
            formatDateTime
        };
    }
});

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
}
app.component('arrowdown', ElementPlusIconsVue.ArrowDown);
app.component('questionfilled', ElementPlusIconsVue.QuestionFilled);
app.component('folderadd', ElementPlusIconsVue.FolderAdd);
app.component('copydocument', ElementPlusIconsVue.CopyDocument);

app.use(ElementPlus, { locale: zhCnLocale });
app.mount('#app');
