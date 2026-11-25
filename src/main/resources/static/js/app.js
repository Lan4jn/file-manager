const { createApp, ref, computed, onMounted, onUnmounted, nextTick, watch, h } = Vue;
const themeHelpers = window.FileManagerTheme || {
    initTheme: (win, doc) => {
        let stored = null;
        try {
            stored = win?.localStorage?.getItem('fm-theme');
        } catch (e) {}
        const next = stored === 'dark' ? 'dark' : 'light';
        if (doc?.documentElement) {
            doc.documentElement.dataset.theme = next;
            doc.documentElement.classList.toggle('dark', next === 'dark');
        }
        return next;
    },
    toggleTheme: (current, storage, doc) => {
        const next = current === 'dark' ? 'light' : 'dark';
        try {
            storage?.setItem('fm-theme', next);
        } catch (e) {}
        if (doc?.documentElement) {
            doc.documentElement.dataset.theme = next;
            doc.documentElement.classList.toggle('dark', next === 'dark');
        }
        return next;
    },
    getSafeStorage: (win) => {
        try {
            return win?.localStorage || null;
        } catch (e) {
            return null;
        }
    }
};
const getStoredToken = () => {
    try {
        return (window.localStorage && window.localStorage.getItem('token')) || '';
    } catch (e) {
        return '';
    }
};
const persistStoredToken = (tokenValue) => {
    try {
        if (window.localStorage) {
            window.localStorage.setItem('token', tokenValue);
            return true;
        }
    } catch (e) {
        // Storage unavailable or quota exceeded
    }
    return false;
};
const clearStoredToken = () => {
    try {
        if (window.localStorage) {
            window.localStorage.removeItem('token');
            return true;
        }
    } catch (e) {
        return false;
    }
    return true;
};
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
const transferUiHelpers = window.TransferUiHelpers || {
    resolveDownloadSessionUrl: () => '/transfer/download/session',
    resolveUploadSessionUrl: () => '/transfer/upload/session',
    resolveUploadCompleteUrl: () => '/transfer/upload/complete',
    createNativeDownload: (url) => {
        if (!url) {
            throw new Error('当前会话未返回可用下载地址');
        }
        const link = document.createElement('a');
        link.href = url;
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        link.remove();
        return url;
    },
    runSingleFileDownload: async (session, navigate) => {
        if (!session || !session.downloadUrl) {
            throw new Error('当前会话未返回可用下载地址');
        }
        const effectiveNavigate = typeof navigate === 'function'
            ? navigate
            : transferUiHelpers.createNativeDownload;
        effectiveNavigate(session.downloadUrl);
        return session.downloadUrl;
    },
    uploadWithXhr: ({ url, method, headers, body, onProgress, onXhr }) => new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open(method || 'PUT', url, true);
        if (typeof onXhr === 'function') {
            onXhr(xhr);
        }
        Object.entries(headers || {}).forEach(([key, value]) => {
            xhr.setRequestHeader(key, value);
        });
        xhr.upload.onprogress = (event) => {
            if (!event.lengthComputable || typeof onProgress !== 'function') {
                return;
            }
            onProgress({
                loaded: event.loaded,
                total: event.total,
                percent: event.total > 0 ? (event.loaded / event.total) * 100 : 0
            });
        };
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve(xhr);
                return;
            }
            reject(new Error('上传失败: HTTP ' + xhr.status));
        };
        xhr.onerror = () => reject(new Error('网络错误'));
        xhr.onabort = () => reject(new Error('上传已取消'));
        xhr.send(body);
    })
};
const fileListUiHelpers = window.FileListUiHelpers || {
    buildFileSyncKey: (bucket, objectName) => String(bucket || '') + '::' + String(objectName || ''),
    isFileSyncing: () => false,
    canOperateFile: () => true,
    applySyncingState: (items) => Array.isArray(items) ? items.slice() : [],
    resolveFileSortState: () => ({ sortBy: 'name', order: 'asc', folderFirst: true, explicitSort: false })
};
const uploadDialogHelpers = window.UploadDialogHelpers || {
    normalizePendingUploadFiles: (files) => (files || []).map((item, index) => {
        if (!item) {
            return null;
        }
        const raw = item.raw || item;
        if (!raw) {
            return null;
        }
        return {
            id: item.uid || ('pending-' + (raw.name || 'file') + '-' + (raw.size || 0) + '-' + (raw.lastModified || 0) + '-' + index),
            name: raw.name || item.name || '待上传文件',
            size: raw.size || item.size || 0,
            raw
        };
    }).filter(Boolean),
    mergePendingUploadFiles: (existingFiles, incomingFiles) => {
        const merged = [];
        const seen = new Set();
        const buildDedupKey = (item) => {
            if (!item) {
                return '';
            }
            const raw = item.raw || {};
            const rawName = raw.name || item.name || '';
            const rawSize = raw.size || item.size || 0;
            const rawLastModified = raw.lastModified || 0;
            if (rawName) {
                return rawName + '::' + rawSize + '::' + rawLastModified;
            }
            return String(item.id || '');
        };
        (uploadDialogHelpers.normalizePendingUploadFiles(existingFiles).concat(uploadDialogHelpers.normalizePendingUploadFiles(incomingFiles)))
            .forEach((item) => {
                const dedupKey = buildDedupKey(item);
                if (!item || !dedupKey || seen.has(dedupKey)) {
                    return;
                }
                seen.add(dedupKey);
                merged.push(item);
            });
        return merged;
    },
    clearClosedUploadTasks: (tasks) => (tasks || []).filter((task) => task && task.status === 'running'),
    applyBatchRename: (items) => items || [],
    shouldFallbackToApplicationUpload: () => false
};
const fileActionVisibilityHelpers = window.FileActionVisibilityHelpers || {
    getRowActionMeta: (code) => ({ code, label: code, icon: '' }),
    splitRowActions: (actions) => ({ primary: actions || [], more: [], hasMore: false })
};
const fileTransferHelpers = window.FileTransferHelpers || {
    buildTransferSummary: (state) => {
        const sources = state?.sources || [];
        return sources.length === 1
            ? ((state?.operation === 'MOVE' ? '移动 ' : '复制 ') + (sources[0]?.fileName || sources[0]?.objectName || '当前项目'))
            : ((state?.operation === 'MOVE' ? '移动已选择的 ' : '复制已选择的 ') + sources.length + ' 个项目');
    },
    normalizeTransferTargetFolder: (targetFolder) => {
        const value = String(targetFolder || '').trim();
        if (!value) {
            return '';
        }
        return value.endsWith('/') ? value : value + '/';
    },
    resolveParentTransferFolder: () => '',
    buildTransferFolderOptions: (rows) => rows || [],
    resolveConflictPolicy: (choice) => {
        if (choice === 'overwrite') {
            return 'OVERWRITE';
        }
        if (choice === 'rename') {
            return 'RENAME';
        }
        return '';
    }
};
const workbenchHelpers = window.WorkbenchHelpers || {
    normalizeOverview: () => ({ defaultWorkbench: false, recentDirectories: [], favoriteDirectories: [], shareReminders: [] }),
    resolveInitialMenu: (currentMenu) => currentMenu || 'buckets',
    hasWorkbenchContent: () => false
};
const shareBannerHelpers = window.ShareBannerHelpers || {
    isShareMessage: () => false,
    buildShareSentBanner: () => ({ title: '', content: '' }),
    buildShareReceivedBanner: () => ({ title: '', content: '' }),
    deriveShareRemindersFromInbox: () => []
};
const shareManagementHelpers = window.ShareManagementHelpers || {
    normalizeShareContentItems: () => [],
    buildVisibleShareContent: (items) => ({ visibleItems: items || [], hiddenCount: 0, hiddenItems: [] }),
    formatShareTargetType: (share) => share && share.targetType === 'USER' ? '分享到用户' : '分享到链接',
    formatShareItemsErrorMessage: (error) => '加载失败：' + (error?.response?.data?.message || error?.message || '分享内容加载失败，请稍后重试')
};
const sharePaginationHelpers = window.SharePaginationHelpers || {
    buildPagedShareParams: (page, size, validOnly) => ({ page: page || 1, size: size || 25, validOnly: !!validOnly }),
    resolvePageAfterFilterChange: () => 1
};
const renameHelpers = window.RenameHelpers || {
    validateRenameTargetName: () => '',
    buildRenamePayload: () => ({})
};
const urlHelpers = window.UrlHelpers || {
    buildApiUrl: (origin, basePath, params) => {
        const url = new URL(basePath, origin);
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
        return url.toString();
    },
    buildShareUrl: (origin, share, includePassword) => {
        const url = new URL('/share/' + (share?.shareCode || ''), origin);
        if (includePassword && share?.hasPassword && share?.password) {
            url.searchParams.set('password', share.password);
        }
        return url.toString();
    }
};
const contextPathHelpers = window.ContextPathHelpers || {
    normalizeContextPath: (value) => String(value || '').trim() === '/' ? '' : String(value || '').trim(),
    joinContextPath: (contextPath, path) => String(contextPath || '') + (String(path || '').startsWith('/') ? String(path || '') : ('/' + String(path || ''))),
    inferContextPath: () => '',
    isContextPathUrlMatch: (contextPath, pathname, appPath) => String(pathname || '') === (String(contextPath || '') + String(appPath || ''))
};
const appContextPath = window.__APP_CONTEXT_PATH__
    || contextPathHelpers.inferContextPath(window.location.pathname, ['/index.html', '/full', '/']);
window.__APP_CONTEXT_PATH__ = appContextPath;
const messageCenterHelpers = window.MessageCenterHelpers || {
    countUnreadShareSources: (messages) => {
        if (!Array.isArray(messages)) {
            return 0;
        }
        const senderSet = new Set(
            messages
                .filter(item => item && !item.read && (!item.messageType || String(item.messageType).toUpperCase() === 'SHARE'))
                .map(item => (item.senderUsername || '').trim())
                .filter(Boolean)
        );
        return senderSet.size;
    },
    buildMessageSubtitle: (message) => {
        const originSenderUsername = (message?.originSenderUsername || '').trim();
        const forwardedByUsername = (message?.forwardedByUsername || '').trim();
        if (originSenderUsername && forwardedByUsername) {
            return '初始分享人 ' + originSenderUsername + '，由 ' + forwardedByUsername + ' 转存';
        }
        const senderUsername = (message?.senderUsername || '').trim();
        return senderUsername ? (senderUsername + ' 分享给你') : '系统消息';
    },
    mergeIncomingMessage: (existingMessages, incomingMessage) => {
        const normalizedExisting = Array.isArray(existingMessages) ? existingMessages : [];
        if (!incomingMessage || incomingMessage.id === null || incomingMessage.id === undefined) {
            return normalizedExisting.slice();
        }
        const nextMessages = normalizedExisting.filter(item => item && item.id !== incomingMessage.id);
        if (!incomingMessage.deleted) {
            nextMessages.unshift(incomingMessage);
        }
        return nextMessages;
    }
};

const zhCnLocale = {
    ...window.ElementPlusLocaleZhCn,
    name: 'zh-cn',
    el: {
        ...window.ElementPlusLocaleZhCn?.el,
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
        const token = ref(getStoredToken());
        if (transferUiHelpers && typeof transferUiHelpers.setTokenProvider === 'function') {
            transferUiHelpers.setTokenProvider(() => token.value);
        }
        const userInfo = ref({});
        const currentMenu = ref('buckets');
        const currentTheme = ref(themeHelpers.initTheme(window, document));
        const toggleTheme = () => {
            const storage = themeHelpers.getSafeStorage ? themeHelpers.getSafeStorage(window) : null;
            currentTheme.value = themeHelpers.toggleTheme(currentTheme.value, storage, document);
        };
        const navigationOpen = ref(false);
        const closeNavigation = () => {
            if (!navigationOpen.value) return;
            navigationOpen.value = false;
            nextTick(() => document.querySelector('.workspace-nav-toggle')?.focus());
        };
        const handleNavigationResize = () => {
            if (window.matchMedia('(min-width: 701px)').matches) navigationOpen.value = false;
        };
        watch(isLoggedIn, value => { if (!value) navigationOpen.value = false; });
        const toggleNavigation = () => {
            if (navigationOpen.value) return closeNavigation();
            navigationOpen.value = true;
            nextTick(() => requestAnimationFrame(() => {
                if (navigationOpen.value) document.querySelector('.workspace-nav-close')?.focus({ preventScroll: true });
            }));
        };
        const loginForm = ref({ username: '', password: '' });
        const loginLoading = ref(false);
        const workbenchOverview = ref(workbenchHelpers.normalizeOverview());
        const workbenchLoading = ref(false);
        const workbenchPreferenceSaving = ref(false);

        // Bucket management
        const bucketList = ref([]);
        const bucketLoading = ref(false);
        const currentBucket = ref('');
        const showBucketDialog = ref(false);
        const newBucketName = ref('');
        const bucketSortBy = ref('name');
        const bucketOrder = ref('asc');
        const bucketStatsRefreshing = ref(false);
        const bucketStatsRefreshStatus = ref({ status: 'IDLE' });
        let bucketStatsPollTimer = null;

        // File management
        const fileList = ref([]);
        const fileLoading = ref(false);
        let fileListRequest = 0;
        let visibleFileListRequest = 0;
        const currentFolder = ref('');
        const breadcrumb = ref([]);
        const selectedFiles = ref([]);
        const fileSortBy = ref('name');
        const fileOrder = ref('asc');
        const folderFirstMode = ref(true);
        // 分页相关
        const filePage = ref(1);
const filePageSize = ref(25);
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
        const runtimeConfig = ref({ httpPort: null, httpsPort: null, forceHttps: false, appVersion: '', fileRowHiddenActions: ['DELETE', 'MOVE', 'COPY'] });
        const showHttpsNotice = computed(() => window.location.protocol === 'http:' && runtimeConfig.value.forceHttps);
        const fileDropActive = ref(false);
        let fileDropDragDepth = 0;
        const uploadDialogDropActive = ref(false);
        const uploadTasks = ref([]);
        const pendingUploadFiles = ref([]);
        const uploadBatchRenameRule = ref({
            prefix: '',
            suffix: '',
            findText: '',
            replaceText: ''
        });
        const syncingFileKeys = ref(new Set());
        const syncingFileMeta = ref({});
        const uploadCancelRequested = ref(false);
        let syncingRetryTimer = null;
        let uploadDialogSelectionTimer = null;

        // Dialogs
        const showUploadDialog = ref(false);
        const uploadRef = ref(null);
        const uploading = ref(false);
        const showFolderDialog = ref(false);
        const showRenameDialog = ref(false);
        const showShareDialog = ref(false);
        const showBatchShareDialog = ref(false);
        const showUserDialog = ref(false);
        const showProfileDialog = ref(false);
        const showCertDialog = ref(false);
        const userCertificates = ref([]);
        const loadUserCertificates = async () => {
            try {
                const res = await certHelpers.getCertificates();
                userCertificates.value = res.data || [];
            } catch (e) {
                ElementPlus.ElMessage.error('加载证书列表失败');
            }
        };
        const showRoleDialog = ref(false);
        const showMinioKeyDialog = ref(false);
        const showHelpDialog = ref(false);
        const showVersionDialog = ref(false);
        const showDiagnosticsDialog = ref(false);
        const showPreviewDialog = ref(false);
        const clientDiagnosticsLogs = ref([]);
        const downloadTasks = ref([]);
        const downloadCenterVisible = ref(false);
        const downloadCenterMinimized = ref(false);
        const transferCenterVisible = ref(false);
        const transferCenterMinimized = ref(false);
        const transferCenterTab = ref('all');
        const transferUploadTasks = ref([]);
        // 控制器持有 File，不放进响应式任务，也不保留已完成项的控制器。
        const multipartControllers = new Map();
        const multipartTargetKeys = new Set();
        let transferSession = 0;

        const messageCenterVisible = ref(false);
        const messageCenterTab = ref('messages');
        const showForwardDialog = ref(false);
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
        const sharePage = ref(1);
        const sharePageSize = ref(25);
        const shareTotal = ref(0);
        const shareValidOnly = ref(false);
        const renameState = ref({
            sourcePath: '',
            targetName: '',
            isFolder: false,
            conflictPolicy: 'OVERWRITE'
        });
        const shareTab = ref('my-shares');
        const inboxList = ref([]);
        const inboxPage = ref(1);
        const inboxPageSize = ref(25);
        const inboxTotal = ref(0);
        const inboxValidOnly = ref(false);
        const inboxLoading = ref(false);
        const recipientList = ref([]);
        const recipientLoading = ref(false);
        const forwardMessageTarget = ref(null);
        const forwardRecipientUserIds = ref([]);
        const feedbackForm = ref({
            category: 'GENERAL',
            title: '',
            body: ''
        });
        const feedbackCategories = [
            { label: '通用反馈', value: 'GENERAL' },
            { label: '产品建议', value: 'SUGGESTION' },
            { label: '故障问题', value: 'BUG' },
            { label: '体验问题', value: 'UX' }
        ];
        let messageSocket = null;
        let messageSocketReconnectTimer = null;
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
            frontendDownloadThresholdMB: 100,
            fileDropMode: 'INSTANT',
            uploadDialogMode: 'INSTANT',
            uploadTransportMode: 'APPLICATION',
            downloadTransportMode: 'APPLICATION',
            uploadTransportPriority: ['DIRECT', 'APPLICATION'],
            downloadTransportPriority: ['DIRECT', 'PROXY', 'APPLICATION'],
            uploadProxyThresholdMB: 256,
            downloadProxyThresholdMB: 128,
            fileRowHiddenActions: ['DELETE', 'MOVE', 'COPY']
        });
        const buildRowActionDefinition = (code, extra = {}) => {
            const meta = fileActionVisibilityHelpers.getRowActionMeta(code);
            return {
                ...meta,
                iconComponent: meta.icon ? ElementPlusIconsVue[meta.icon] : null,
                ...extra
            };
        };
        const fileRowActionOptions = ['DOWNLOAD', 'SHARE', 'PREVIEW', 'RENAME', 'DELETE', 'MOVE', 'COPY']
            .map(code => buildRowActionDefinition(code, { value: code }));
        const showTransferDialog = ref(false);
        const transferState = ref({
            operation: 'COPY',
            sources: [],
            targetFolder: '',
            folderStack: [''],
            currentFolder: '',
            folderOptions: []
        });
        const transferSummary = computed(() => fileTransferHelpers.buildTransferSummary(transferState.value));
        const transferTargetLabel = computed(() => transferState.value.targetFolder || '根目录');
        const uploadDialogModeOptions = [
            { label: '手动上传', value: 'MANUAL' },
            { label: '立即上传', value: 'INSTANT' }
        ];
        const uploadTransportOptions = [
            { label: 'MinIO 直传', value: 'DIRECT' },
            { label: '应用代理上传', value: 'APPLICATION' }
        ];
        const downloadTransportOptions = [
            { label: 'MinIO 直连下载', value: 'DIRECT' },
            { label: 'Jar 流式代理', value: 'PROXY' },
            { label: '应用下载接口', value: 'APPLICATION' }
        ];
        const uploadTransportLabelMap = Object.fromEntries(uploadTransportOptions.map(item => [item.value, item.label]));
        const downloadTransportLabelMap = Object.fromEntries(downloadTransportOptions.map(item => [item.value, item.label]));

        // Share config
        const shareConfig = ref({
            batchShareMaxSizeGB: 2,
            batchDownloadMaxSizeGB: 2,
            expireHours: 168
        });

        // API
        const api = axios.create({
            baseURL: contextPathHelpers.joinContextPath(appContextPath, '/api'),
            timeout: 30000
        });
        const certHelpers = createClientCertificateHelpers(api);

        const stringifyDiagnosticDetail = (detail) => {
            if (detail === null || detail === undefined) {
                return '';
            }
            if (typeof detail === 'string') {
                return detail;
            }
            try {
                return JSON.stringify(detail, null, 2);
            } catch (error) {
                return String(detail);
            }
        };

        const recordClientDiagnostic = (level, event, detail) => {
            const entry = {
                id: 'diag-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8),
                time: new Date().toLocaleString(),
                level: level || 'info',
                event: event || 'event',
                detail: stringifyDiagnosticDetail(detail)
            };
            clientDiagnosticsLogs.value = [entry, ...clientDiagnosticsLogs.value].slice(0, 100);
            return entry;
        };

        const clearClientDiagnostics = () => {
            clientDiagnosticsLogs.value = [];
        };

        const copyClientDiagnostics = async () => {
            const payload = clientDiagnosticsLogs.value
                .map(item => '[' + item.time + '] ' + item.level + ' ' + item.event + '\n' + (item.detail || ''))
                .join('\n\n');
            try {
                await navigator.clipboard.writeText(payload || '暂无诊断日志');
                ElementPlus.ElMessage.success('诊断日志已复制');
            } catch (error) {
                ElementPlus.ElMessage.error('复制失败，请在面板中手动选择复制');
            }
        };

        const isCurrentTransferSession = (session) => session === transferSession && (!!token.value || isLoggedIn.value);

        const requireTransferSession = (session) => {
            if (!isCurrentTransferSession(session)) {
                const error = new Error('传输的登录会话已失效');
                error.name = 'AbortError';
                throw error;
            }
        };

        api.interceptors.request.use(config => {
            if (config.transferSession !== undefined) {
                requireTransferSession(config.transferSession);
            }
            if (token.value) {
                config.headers.Authorization = 'Bearer ' + token.value;
            }
            return config;
        });

        const isStaleTransferRequest = (config) => config?.transferSession !== undefined && config.transferSession !== transferSession;

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
                if (isStaleTransferRequest(error.config)) return Promise.reject(error);
                recordClientDiagnostic('error', 'http-error', {
                    url: error.config && error.config.url,
                    method: error.config && error.config.method,
                    status: error.response && error.response.status,
                    message: error.response && error.response.data ? error.response.data.message : error.message,
                    data: error.response && error.response.data
                });
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
        const canOpenDiagnostics = computed(() => hasApiPermission('SYSTEM_CONFIG_ACCESS'));

        const currentViewTitle = computed(() => {
            const titles = {
                buckets: '存储空间',
                workbench: '工作台',
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
                workbench: '继续最近的目录操作，集中查看常用目录入口。',
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
        const normalizeTransferTask = (task, direction) => {
            const { status, controller } = task;
            const upload = direction === 'upload';
            return {
                id: direction + ':' + task.id,
                direction,
                name: task.name,
                status,
                step: task.step,
                transferredBytes: (upload ? task.uploadedBytes : task.downloadedBytes) || 0,
                totalBytes: task.totalBytes || 0,
                speedBytesPerSecond: task.speedBytesPerSecond || 0,
                progress: task.progress || 0,
                completedParts: task.completedParts || 0,
                totalParts: task.totalParts || 0,
                resumed: !!task.resumed,
                error: task.error || '',
                canPause: status === 'running' && (upload || !!controller?.pause),
                canResume: status === 'paused' && (upload || !!controller?.resume),
                canRetry: status === 'failed' && (upload ? !task.controllerUnavailable : !!task.retryHandler),
                canCancel: (upload && status === 'cancelled' && !!task.cleanupFailed)
                    || ((status === 'running' || status === 'paused' || status === 'failed')
                        && (upload ? !task.controllerUnavailable : !!controller?.cancel || (status === 'running' && !!task.abortController)))
            };
        };
        const unifiedTransferTasks = computed(() => [
            ...transferUploadTasks.value.map(task => normalizeTransferTask(task, 'upload')),
            ...downloadTasks.value.map(task => normalizeTransferTask(task, 'download'))
        ]);
        const activeTransferCount = computed(() => unifiedTransferTasks.value.filter(task => task.status === 'running').length);
        const completedTransferCount = computed(() => unifiedTransferTasks.value.filter(task => task.status === 'completed').length);
        const filteredTransferTasks = computed(() => transferCenterTab.value === 'all'
            ? unifiedTransferTasks.value
            : unifiedTransferTasks.value.filter(task => task.direction === transferCenterTab.value));
        const transferTabCounts = computed(() => ({
            all: unifiedTransferTasks.value.length,
            upload: transferUploadTasks.value.length,
            download: downloadTasks.value.length
        }));
        const clearCompletedTransfers = () => {
            transferUploadTasks.value = transferUploadTasks.value.filter(task => task.status !== 'completed');
            downloadTasks.value = downloadTasks.value.filter(task => task.status !== 'completed');
        };

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
            return messageCenterHelpers.countUnreadShareSources(inboxList.value);
        });
        const messageCenterMessages = computed(() => inboxList.value);
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
        const isCurrentDirectoryFavorite = computed(() => {
            if (!currentBucket.value || !currentFolder.value) {
                return false;
            }
            return (workbenchOverview.value.favoriteDirectories || []).some(item => item
                && item.bucketName === currentBucket.value
                && item.directoryPath === currentFolder.value);
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
            error: '',
            controller: null,
            abortController: null,
            retryHandler: null
        });

        const addDownloadTask = (task) => {
            downloadTasks.value.unshift(task);
            openDownloadCenter();
            return task;
        };

        const getDownloadTask = (taskId) => {
            return downloadTasks.value.find(task => task.id === taskId) || null;
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

        const downloadTerminalPatch = { controller: null, abortController: null, resumeHandler: null, retryHandler: null, runKey: null, error: '', speedBytesPerSecond: 0 };

        const completeDownloadTask = (taskId, patch = {}) => {
            updateDownloadTask(taskId, {
                ...downloadTerminalPatch,
                status: 'completed',
                progress: 100,
                ...patch
            });
        };

        const markDelegatedDownloadTask = (taskId, patch = {}) => {
            updateDownloadTask(taskId, {
                ...downloadTerminalPatch,
                status: 'delegated',
                progress: 100,
                ...patch
            });
        };

        const markPausedDownloadTask = (taskId, state = {}) => {
            updateDownloadTask(taskId, {
                downloadedBytes: state.downloadedBytes ?? getDownloadTask(taskId)?.downloadedBytes ?? 0,
                progress: state.progress ?? getDownloadTask(taskId)?.progress ?? 0,
                status: 'paused',
                step: '已暂停',
                error: '',
                speedBytesPerSecond: 0
            });
        };

        const markCancelledDownloadTask = (taskId) => {
            updateDownloadTask(taskId, {
                ...downloadTerminalPatch,
                status: 'cancelled',
                step: '已取消'
            });
        };

        const failDownloadTask = (taskId, errorMessage) => {
            if (!getDownloadTask(taskId)) return;
            updateDownloadTask(taskId, {
                status: 'failed',
                step: '下载失败',
                speedBytesPerSecond: 0,
                abortController: null,
                error: errorMessage || '下载失败'
            });
            openDownloadCenter();
        };

        const createUploadTask = (file) => ({
            id: 'upload-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
            name: file?.name || '上传文件',
            status: 'running',
            step: '准备上传',
            progress: 0,
            uploadedBytes: 0,
            totalBytes: file?.size || 0,
            error: '',
            xhr: null,
            objectName: ''
        });

        const updateUploadTask = (taskId, patch) => {
            const index = uploadTasks.value.findIndex(task => task.id === taskId);
            if (index < 0) {
                return;
            }
            uploadTasks.value[index] = {
                ...uploadTasks.value[index],
                ...patch
            };
        };

        const removeUploadTask = (taskId) => {
            uploadTasks.value = uploadTasks.value.filter(task => task.id !== taskId);
        };

        const clearFinishedUploadTasks = () => {
            uploadTasks.value = uploadTasks.value.filter(task => task.status === 'running');
        };

        const registerSyncingFile = (bucket, uploadInfo) => {
            if (!uploadInfo || !uploadInfo.objectName) {
                return;
            }
            const key = fileListUiHelpers.buildFileSyncKey(bucket, uploadInfo.objectName);
            const nextKeys = new Set(syncingFileKeys.value);
            nextKeys.add(key);
            syncingFileKeys.value = nextKeys;
            syncingFileMeta.value = {
                ...syncingFileMeta.value,
                [key]: {
                    bucket,
                    objectName: uploadInfo.objectName,
                    fileName: uploadInfo.fileName || uploadInfo.objectName.split('/').filter(Boolean).pop() || uploadInfo.objectName,
                    size: uploadInfo.size || 0,
                    uploadTime: uploadInfo.uploadTime || '',
                    retryCount: 0
                }
            };
        };

        const clearSyncingFile = (bucket, objectName) => {
            const key = fileListUiHelpers.buildFileSyncKey(bucket, objectName);
            const nextKeys = new Set(syncingFileKeys.value);
            nextKeys.delete(key);
            syncingFileKeys.value = nextKeys;
            const nextMeta = { ...syncingFileMeta.value };
            delete nextMeta[key];
            syncingFileMeta.value = nextMeta;
        };

        const currentFolderMatchesObject = (objectName) => {
            const normalizedObject = objectName || '';
            const lastSlashIndex = normalizedObject.lastIndexOf('/');
            const parent = lastSlashIndex >= 0 ? normalizedObject.slice(0, lastSlashIndex + 1) : '';
            return parent === (currentFolder.value || '');
        };

        const normalizeFileRow = (item) => ({
            ...(item || {}),
            bucket: item?.bucket || item?.bucketName || currentBucket.value
        });

        const buildSyncingPlaceholderRows = (items) => {
            const rows = [];
            const existingObjectNames = new Set((items || []).map(item => item.objectName));
            Object.values(syncingFileMeta.value || {}).forEach(meta => {
                if (!meta || meta.bucket !== currentBucket.value || !currentFolderMatchesObject(meta.objectName)) {
                    return;
                }
                if (existingObjectNames.has(meta.objectName)) {
                    return;
                }
                rows.push({
                    bucket: meta.bucket,
                    objectName: meta.objectName,
                    fileName: meta.fileName,
                    size: meta.size,
                    isFolder: false,
                    uploadTime: '',
                    syncing: true
                });
            });
            return rows;
        };

        const decorateFileRows = (items) => {
            const normalizedRows = (items || []).map(normalizeFileRow);
            const syncingRows = fileListUiHelpers.applySyncingState(normalizedRows, syncingFileKeys.value);
            return syncingRows.concat(buildSyncingPlaceholderRows(syncingRows));
        };

        const scheduleSyncingRetry = () => {
            if (syncingRetryTimer || syncingFileKeys.value.size === 0 || !token.value) {
                return;
            }
            const session = transferSession;
            syncingRetryTimer = setTimeout(async () => {
                syncingRetryTimer = null;
                if (!isCurrentTransferSession(session)) return;
                const metaEntries = Object.values(syncingFileMeta.value || {});
                if (!metaEntries.length) {
                    return;
                }
                await Promise.all(metaEntries.map(async (meta) => {
                    try {
                        await api.get('/file/info', {
                            params: {
                                objectName: meta.objectName,
                                bucket: meta.bucket
                            },
                            transferSession: session
                        });
                        if (!isCurrentTransferSession(session)) return;
                        clearSyncingFile(meta.bucket, meta.objectName);
                    } catch (error) {
                        if (!isCurrentTransferSession(session)) return;
                        const key = fileListUiHelpers.buildFileSyncKey(meta.bucket, meta.objectName);
                        const currentMeta = syncingFileMeta.value[key];
                        if (currentMeta) {
                            syncingFileMeta.value = {
                                ...syncingFileMeta.value,
                                [key]: {
                                    ...currentMeta,
                                    retryCount: (currentMeta.retryCount || 0) + 1
                                }
                            };
                        }
                    }
                }));
                if (!isCurrentTransferSession(session)) return;
                if (currentMenu.value === 'files') {
                    await loadFiles();
                }
                if (syncingFileKeys.value.size > 0) {
                    scheduleSyncingRetry();
                }
            }, 2000);
        };

        const stopSyncingRetry = () => {
            if (!syncingRetryTimer) {
                return;
            }
            clearTimeout(syncingRetryTimer);
            syncingRetryTimer = null;
        };

        const buildAuthorizedHeaders = () => {
            if (!token.value) {
                return {};
            }
            return {
                Authorization: 'Bearer ' + token.value
            };
        };

        const buildDownloadHeadersForUrl = (url) => {
            if (!url) {
                return {};
            }
            try {
                const resolved = new URL(url, window.location.origin);
                if (resolved.origin === window.location.origin
                    && contextPathHelpers.isContextPathUrlMatch(appContextPath, resolved.pathname, '/api/file/download')) {
                    return buildAuthorizedHeaders();
                }
            } catch (error) {
                return {};
            }
            return {};
        };

        const extractUploadErrorMessage = (error, fallbackMessage = '上传失败') => {
            if (!error) {
                return fallbackMessage;
            }
            const responseMessage = error.response?.data?.message;
            const httpStatus = Number(error.response?.status || 0);
            const rawMessage = typeof responseMessage === 'string' && responseMessage.trim()
                ? responseMessage.trim()
                : (typeof error.message === 'string' && error.message.trim() ? error.message.trim() : '');
            const normalized = rawMessage.toLowerCase();

            if (normalized.includes('上传已取消')) {
                return '已取消上传';
            }
            if (normalized.includes('网络错误')) {
                return '上传失败：网络错误，请检查与服务端或 MinIO 的连接';
            }
            if (normalized.includes('确认失败')) {
                return '上传失败：文件已传到对象存储，但服务端确认失败';
            }
            if (httpStatus === 401 || normalized.includes('401')) {
                return '上传失败：登录已失效或未认证';
            }
            if (httpStatus === 403 || normalized.includes('403') || normalized.includes('forbidden')) {
                return '上传失败：权限不足，当前账号没有上传权限';
            }
            if (httpStatus === 413 || normalized.includes('文件大小超出限制') || normalized.includes('payload too large')) {
                return '上传失败：文件太大，超过系统限制';
            }
            if (normalized.includes('不允许上传此类型文件') || normalized.includes('extension')) {
                return '上传失败：文件类型不允许';
            }
            if (normalized.includes('minio') || normalized.includes('s3') || normalized.includes('对象存储')) {
                return '上传失败：对象存储不可达或响应异常';
            }
            if (rawMessage) {
                return '上传失败：' + rawMessage;
            }
            return fallbackMessage;
        };

        const canUseNativeDownload = (url) => {
            if (!url) {
                return false;
            }
            try {
                const resolved = new URL(url, window.location.origin);
                return resolved.origin !== window.location.origin
                    || resolved.pathname.indexOf(contextPathHelpers.joinContextPath(appContextPath, '/api/transfer/download/stream/')) === 0;
            } catch (error) {
                return false;
            }
        };

        const createUploadTraceId = () => {
            const randomPart = Math.random().toString(36).slice(2, 10);
            return 'up-' + Date.now().toString(36) + '-' + randomPart;
        };

        const logUploadTrace = (traceId, stage, detail = {}) => {
            if (!window.console || typeof window.console.info !== 'function') {
                return;
            }
            window.console.info('[upload-trace]', Object.assign({
                traceId,
                stage,
                time: new Date().toISOString()
            }, detail));
        };

        const uploadFileRequest = async ({ file, onProgress, onXhr, targetFileName, bucket, folder, session: ownerSession }) => {
            requireTransferSession(ownerSession);
            const uploadTraceId = createUploadTraceId();
            const resolvedTargetFileName = targetFileName || file.name;
            logUploadTrace(uploadTraceId, 'session:start', {
                originalFileName: file.name,
                targetFileName: resolvedTargetFileName,
                size: file.size,
                contentType: file.type || 'application/octet-stream',
                folder,
                bucket
            });
            const sessionRes = await api.post(transferUiHelpers.resolveUploadSessionUrl(), {
                fileName: resolvedTargetFileName,
                originalFileName: file.name,
                targetFileName: resolvedTargetFileName,
                size: file.size,
                contentType: file.type || 'application/octet-stream',
                folder,
                bucket,
                uploadTraceId
            }, { transferSession: ownerSession });
            requireTransferSession(ownerSession);
            const session = sessionRes.data || sessionRes || {};
            logUploadTrace(uploadTraceId, 'session:created', {
                mode: session.mode,
                bucket: session.bucket,
                objectName: session.objectName,
                fileName: session.fileName,
                hasUploadUrl: !!session.uploadUrl,
                hasUploadToken: !!session.uploadToken
            });
            const retryCompleteUpload = async (uploadToken, maxAttempts = 3) => {
                let lastError = null;
                for (let attempt = 1; attempt <= maxAttempts; attempt++) {
                    requireTransferSession(ownerSession);
                    try {
                        logUploadTrace(uploadTraceId, 'complete:start', { attempt, maxAttempts });
                        const completeRes = await api.post(transferUiHelpers.resolveUploadCompleteUrl(), {
                            uploadToken,
                            uploadTraceId
                        }, { transferSession: ownerSession });
                        logUploadTrace(uploadTraceId, 'complete:success', { attempt });
                        return completeRes;
                    } catch (error) {
                        lastError = error;
                        logUploadTrace(uploadTraceId, 'complete:error', {
                            attempt,
                            maxAttempts,
                            message: extractUploadErrorMessage(error, error.message || '上传确认失败')
                        });
                        if (attempt < maxAttempts) {
                            await new Promise((resolve) => setTimeout(resolve, attempt * 500));
                        }
                    }
                }
                throw lastError || new Error('上传确认失败');
            };

            const uploadThroughApplication = () => new Promise((resolve, reject) => {
                requireTransferSession(ownerSession);
                const xhr = new XMLHttpRequest();
                logUploadTrace(uploadTraceId, 'application:start', {
                    originalFileName: file.name,
                    targetFileName: resolvedTargetFileName,
                    size: file.size
                });
                xhr.open('POST', uploadUrl.value, true);
                if (typeof onXhr === 'function') {
                    onXhr(xhr);
                }
                Object.entries(uploadHeaders.value || {}).forEach(([key, value]) => {
                    xhr.setRequestHeader(key, value);
                });
                xhr.upload.onprogress = (event) => {
                    if (!event.lengthComputable || typeof onProgress !== 'function') {
                        return;
                    }
                    onProgress({
                        loaded: event.loaded,
                        total: event.total,
                        percent: event.total > 0 ? (event.loaded / event.total) * 100 : 0
                    });
                };
                xhr.onload = () => {
                    logUploadTrace(uploadTraceId, 'application:response', { status: xhr.status });
                    if (xhr.status >= 200 && xhr.status < 300) {
                        try {
                            const response = JSON.parse(xhr.responseText);
                            if (response.code === 200) {
                                logUploadTrace(uploadTraceId, 'application:success', {
                                    objectName: response.data && response.data.objectName,
                                    fileName: response.data && response.data.fileName,
                                    size: response.data && response.data.size
                                });
                                resolve({ xhr, response, mode: 'APPLICATION' });
                                return;
                            }
                            logUploadTrace(uploadTraceId, 'application:error', { message: response.message || '上传失败' });
                            reject(new Error(response.message || '上传失败'));
                        } catch (error) {
                            logUploadTrace(uploadTraceId, 'application:error', { message: error.message || '响应解析失败' });
                            reject(error);
                        }
                        return;
                    }
                    try {
                        const response = JSON.parse(xhr.responseText);
                        logUploadTrace(uploadTraceId, 'application:error', { status: xhr.status, message: response.message || '' });
                        reject(new Error(response.message || ('上传失败: HTTP ' + xhr.status)));
                    } catch (error) {
                        logUploadTrace(uploadTraceId, 'application:error', { status: xhr.status, message: 'HTTP ' + xhr.status });
                        reject(new Error('上传失败: HTTP ' + xhr.status));
                    }
                };
                xhr.onerror = () => {
                    logUploadTrace(uploadTraceId, 'application:error', { message: '网络错误' });
                    reject(new Error('网络错误'));
                };
                xhr.onabort = () => {
                    logUploadTrace(uploadTraceId, 'application:abort');
                    reject(new Error('上传已取消'));
                };
                const formData = new FormData();
                formData.append('file', file);
                Object.entries({ folder, bucket }).forEach(([key, value]) => {
                    formData.append(key, value);
                });
                formData.append('targetFileName', resolvedTargetFileName);
                formData.append('uploadTraceId', uploadTraceId);
                xhr.send(formData);
            });

            const uploadDirectly = async () => {
                logUploadTrace(uploadTraceId, 'direct:start', {
                    bucket: session.bucket,
                    objectName: session.objectName,
                    fileName: session.fileName,
                    size: file.size
                });
                await transferUiHelpers.uploadWithXhr({
                    url: session.uploadUrl,
                    method: 'PUT',
                    headers: session.headers || {},
                    body: file,
                    onProgress,
                    onXhr
                });
                logUploadTrace(uploadTraceId, 'direct:put-success', {
                    bucket: session.bucket,
                    objectName: session.objectName
                });
                const completeRes = await retryCompleteUpload(session.uploadToken);
                logUploadTrace(uploadTraceId, 'direct:success', {
                    objectName: completeRes.data && completeRes.data.objectName,
                    fileName: completeRes.data && completeRes.data.fileName,
                    size: completeRes.data && completeRes.data.size
                });
                return {
                    response: { code: 200, data: completeRes.data || completeRes },
                    mode: 'DIRECT'
                };
            };

            if (session.mode === 'DIRECT') {
                try {
                    return await uploadDirectly();
                } catch (error) {
                    if (error.message === '上传已取消') {
                        throw error;
                    }
                    if (uploadDialogHelpers.shouldFallbackToApplicationUpload(error)) {
                        logUploadTrace(uploadTraceId, 'direct:fallback-application', {
                            message: extractUploadErrorMessage(error, error.message || '上传确认失败')
                        });
                        return uploadThroughApplication();
                    }
                    if (session.uploadToken) {
                        throw new Error('文件已上传到对象存储，但上传确认失败: ' + extractUploadErrorMessage(error, '请稍后重试'));
                    }
                    return uploadThroughApplication();
                }
            }

            return uploadThroughApplication();
        };

        const promptUndoUploadedFiles = (uploadedItems, bucketName, session) => {
            if (!uploadedItems || uploadedItems.length === 0) {
                return;
            }
            const message = uploadedItems.length === 1
                ? ('文件 "' + uploadedItems[0].fileName + '" 上传成功')
                : ('本次成功上传 ' + uploadedItems.length + ' 个文件');
            let undoing = false;
            const notification = ElementPlus.ElNotification({
                title: '',
                duration: 30000,
                showClose: true,
                position: 'top-right',
                customClass: 'upload-success-notification',
                message: h('div', { class: 'upload-success-notification__body' }, [
                    h('div', { class: 'upload-success-notification__icon' }, [
                        h(ElementPlus.ElIcon, null, () => h(ElementPlusIconsVue.CircleCheck))
                    ]),
                    h('div', { class: 'upload-success-notification__text' }, message),
                    h(ElementPlus.ElButton, {
                        text: true,
                        type: 'primary',
                        class: 'upload-success-notification__action',
                        onClick: async () => {
                            if (undoing || !isCurrentTransferSession(session)) {
                                notification.close();
                                return;
                            }
                            undoing = true;
                            try {
                                await api.delete('/file/batch-delete', {
                                    params: {
                                        objectNames: uploadedItems.map(item => item.objectName).join(','),
                                        bucket: bucketName || currentBucket.value
                                    },
                                    transferSession: session
                                });
                                if (!isCurrentTransferSession(session)) return;
                                uploadedItems.forEach(item => clearSyncingFile(bucketName || currentBucket.value, item.objectName));
                                notification.close();
                                ElementPlus.ElMessage.success('已撤销本次上传');
                                await loadFiles();
                            } catch (error) {
                                ElementPlus.ElMessage.error('撤销上传失败: ' + (error.response?.data?.message || error.message || '未知错误'));
                            } finally {
                                undoing = false;
                            }
                        }
                    }, () => '撤销上传')
                ])
            });
        };

        const startMultipartUpload = (uploads, file, { bucket, folder, session, targetFileName, targetKey }) => {
            multipartTargetKeys.add(targetKey);
            const task = createUploadTask({ name: targetFileName, size: file.size });
            const { xhr, objectName, ...transferTask } = task;
            transferUploadTasks.value.unshift({ ...transferTask, targetKey });
            transferCenterVisible.value = true;
            transferCenterMinimized.value = false;

            const applyState = (state) => {
                if (!isCurrentTransferSession(session)) return;
                const row = transferUploadTasks.value.find(item => item.id === task.id);
                if (!row) return;
                const steps = { running: '正在分片上传', paused: '已暂停', completed: '上传完成', failed: '上传失败', cancelled: '已取消' };
                Object.assign(row, state, {
                    step: steps[state.status],
                    error: state.status === 'failed' ? extractUploadErrorMessage({ message: state.error }) : '',
                    speedBytesPerSecond: state.status === 'running' ? state.speedBytesPerSecond : 0
                });
                if (state.status === 'completed') {
                    multipartControllers.delete(task.id);
                    multipartTargetKeys.delete(targetKey);
                }
                if (state.status === 'completed' && currentMenu.value === 'files'
                    && currentBucket.value === bucket && currentFolder.value === folder) {
                    void loadFiles({ silent: true });
                }
            };

            try {
                const controller = uploads.createMultipartUploader({
                    file,
                    bucket,
                    folder,
                    targetFileName,
                    apiBasePath: contextPathHelpers.joinContextPath(appContextPath, '/api'),
                    getAuthHeaders: () => {
                        requireTransferSession(session);
                        return { Authorization: 'Bearer ' + token.value };
                    },
                    onProgress: applyState
                });
                multipartControllers.set(task.id, controller);
                void controller.start().catch(() => { /* 引擎已通过 onProgress 上报失败状态。 */ });
            } catch (error) {
                multipartControllers.delete(task.id);
                multipartTargetKeys.delete(targetKey);
                applyState({ status: 'failed', error: error.message, controllerUnavailable: true });
            }
        };

        const runUploadBatch = async (files, options = {}) => {
            const uploadItems = (files || []).map((item) => {
                if (!item) {
                    return null;
                }
                if (item.raw) {
                    return {
                        raw: item.raw,
                        targetName: String(item.targetName || item.name || item.raw.name || '').trim() || item.raw.name
                    };
                }
                return {
                    raw: item,
                    targetName: item.name
                };
            }).filter(Boolean);
            if (!uploadItems.length) {
                ElementPlus.ElMessage.warning('请先选择要上传的文件');
                return;
            }
            if (uploading.value) {
                ElementPlus.ElMessage.warning('已有上传任务进行中');
                return;
            }
            const {
                bucket: uploadBucket = currentBucket.value,
                folder: uploadFolder = currentFolder.value,
                session = transferSession
            } = options;
            if (!isCurrentTransferSession(session)) return;
            const uploads = window.FileManagerUploads;
            const ordinaryTargets = new Set();
            const folderPrefix = uploadFolder && !uploadFolder.endsWith('/') ? uploadFolder + '/' : uploadFolder;
            const ordinaryItems = uploadItems.filter(({ raw: file, targetName }) => {
                const targetFileName = String(targetName || file.name).trim();
                const dot = targetFileName.lastIndexOf('.');
                const extStart = dot >= 0 && dot < targetFileName.length - 1 ? dot : targetFileName.length;
                // 服务端只替换文件主名里的对象名非法字符，扩展名原样保留。
                const objectName = targetFileName.slice(0, extStart).replace(/[\\/:*?"<>|]/g, '_') + targetFileName.slice(extStart);
                const targetKey = JSON.stringify([uploadBucket, folderPrefix + objectName]);
                const multipart = uploads?.shouldUseMultipartUpload?.(file.size);
                if (multipartTargetKeys.has(targetKey) || (multipart && ordinaryTargets.has(targetKey))) {
                    ElementPlus.ElMessage.warning('已有同名上传任务：' + targetFileName);
                    return false;
                }
                if (!multipart) {
                    ordinaryTargets.add(targetKey);
                    return true;
                }
                startMultipartUpload(uploads, file, { bucket: uploadBucket, folder: uploadFolder, session, targetFileName, targetKey });
                return false;
            });
            if (!ordinaryItems.length) {
                if (options.closeMultipartDialog !== false) {
                    uploadRef.value?.clearFiles();
                    showUploadDialog.value = false;
                }
                return;
            }

            uploading.value = true;
            uploadCancelRequested.value = false;
            clearFinishedUploadTasks();
            const uploadedItems = [];
            try {
                for (const uploadItem of ordinaryItems) {
                    const file = uploadItem.raw;
                    if (uploadCancelRequested.value) {
                        break;
                    }
                    const task = createUploadTask(file);
                    task.name = uploadItem.targetName || task.name;
                    uploadTasks.value = [task, ...uploadTasks.value];
                    updateUploadTask(task.id, { step: '正在上传', progress: 0 });
                    try {
                        const result = await uploadFileRequest({
                            file,
                            targetFileName: uploadItem.targetName,
                            bucket: uploadBucket,
                            folder: uploadFolder,
                            session,
                            onXhr: (xhr) => {
                                updateUploadTask(task.id, { xhr });
                            },
                            onProgress: (progress) => {
                                updateUploadTask(task.id, {
                                    step: '正在上传',
                                    progress: progress.percent,
                                    uploadedBytes: progress.loaded,
                                    totalBytes: progress.total
                                });
                            }
                        });
                        if (!isCurrentTransferSession(session)) return;
                        const uploadInfo = result.response.data || {};
                        uploadedItems.push(uploadInfo);
                        registerSyncingFile(uploadBucket, uploadInfo);
                        updateUploadTask(task.id, {
                            status: 'completed',
                            step: '上传完成，等待信息同步',
                            progress: 100,
                            uploadedBytes: file.size || task.uploadedBytes,
                            totalBytes: file.size || task.totalBytes,
                            objectName: uploadInfo.objectName || '',
                            xhr: null
                        });
                    } catch (error) {
                        if (!isCurrentTransferSession(session)) return;
                        updateUploadTask(task.id, {
                            status: error.message === '上传已取消' ? 'cancelled' : 'failed',
                            step: error.message === '上传已取消' ? '已取消' : '上传失败',
                            error: extractUploadErrorMessage(error),
                            xhr: null
                        });
                        if (uploadCancelRequested.value || error.message === '上传已取消') {
                            break;
                        }
                    }
                }
                if (uploadRef.value) {
                    uploadRef.value.clearFiles();
                }
                if (currentBucket.value === uploadBucket && currentFolder.value === uploadFolder && currentMenu.value === 'files') {
                    await loadFiles();
                }
                if (!isCurrentTransferSession(session)) return;
                scheduleSyncingRetry();
                if (uploadedItems.length > 0) {
                    promptUndoUploadedFiles(uploadedItems, uploadBucket, session);
                }
                if (options.closeDialog !== false) {
                    showUploadDialog.value = false;
                }
            } finally {
                if (isCurrentTransferSession(session)) {
                    uploading.value = false;
                    uploadCancelRequested.value = false;
                }
            }
        };

        const toQueryUrl = (basePath, params) => {
            return urlHelpers.buildApiUrl(window.location.origin, appContextPath, '/api' + basePath, params);
        };

        const openNativeDownload = (url) => transferUiHelpers.createNativeDownload(url);

        const shouldUseTaskCenterForFile = (file) => {
            if (!file || file.isFolder || !downloadHelpers.shouldUseChunkedDownload) {
                return false;
            }
            return downloadHelpers.shouldUseChunkedDownload(
                file.size || 0,
                fileConfig.value.frontendDownloadThresholdMB || 100
            );
        };

        const requestFileDownloadSession = async (objectName, bucket, ownerSession) => {
            requireTransferSession(ownerSession);
            const payload = { scene: 'FILE', bucket, objectName };
            const result = await api.post(transferUiHelpers.resolveDownloadSessionUrl(payload), payload, {
                transferSession: ownerSession
            });
            requireTransferSession(ownerSession);
            return result.data || result;
        };

        const buildChunkedRestartHandler = (file, taskId, target) => async () => {
            const task = getDownloadTask(taskId);
            if (!task || !isCurrentTransferSession(target.ownerSession)) return;
            updateDownloadTask(taskId, { runKey: null, status: 'running', step: '准备重新下载', error: '' });
            try {
                const fileHandle = downloadHelpers.supportsStreamToDisk?.()
                    ? await window.showSaveFilePicker({ suggestedName: file.fileName || 'download.bin' }) : null;
                const session = await requestFileDownloadSession(file.objectName, target.bucket, target.ownerSession);
                await runChunkedFileDownload(file, session, taskId, fileHandle, target);
            } catch (error) {
                if (!isCurrentTransferSession(target.ownerSession)) return;
                if (error?.name === 'AbortError') {
                    updateDownloadTask(taskId, { status: task.status, step: task.step, error: task.error });
                } else {
                    updateDownloadTask(taskId, { retryHandler: task.restartHandler });
                    failDownloadTask(taskId, error?.message || '重新下载失败');
                }
            }
        };

        const runChunkedFileDownload = async (file, session, taskId, fileHandle, { bucket, ownerSession }) => {
            requireTransferSession(ownerSession);
            const currentTaskId = taskId || addDownloadTask(createDownloadTask({
                type: 'single-file',
                name: file.fileName,
                step: '正在下载大文件',
                totalBytes: file.size || 0
            })).id;
            let latestSession = session;
            let controller;
            let attemptId = 0;
            const objectName = file.objectName;
            const runKey = Symbol();
            const writesToDisk = !!fileHandle || !!downloadHelpers.supportsStreamToDisk?.();
            const isCurrent = () => isCurrentTransferSession(ownerSession) && getDownloadTask(currentTaskId)?.runKey === runKey;
            const refreshUrl = async () => {
                latestSession = await requestFileDownloadSession(objectName, bucket, ownerSession);
                return latestSession.downloadUrl;
            };
            const delegateToBrowser = (url) => {
                if (!isCurrent()) return;
                openNativeDownload(url);
                markDelegatedDownloadTask(currentTaskId, {
                    step: '浏览器下载中',
                    totalBytes: file.size || 0,
                    downloadedBytes: file.size || 0
                });
            };
            const handleFailure = (error) => {
                if (!isCurrent()) return;
                const state = controller?.getState();
                if (state?.status === 'cancelled' || error?.name === 'AbortError') {
                    markCancelledDownloadTask(currentTaskId);
                    return;
                }
                const { downloadUrl, fallbackUrl } = latestSession;
                const hasWrittenData = writesToDisk && state?.downloadedBytes > 0;
                if (!hasWrittenData && fallbackUrl && fallbackUrl !== downloadUrl && canUseNativeDownload(fallbackUrl)) {
                    recordClientDiagnostic('warn', 'download-fallback', {
                        fileName: file.fileName,
                        directUrl: downloadUrl,
                        fallbackUrl,
                        reason: error?.message || ''
                    });
                    controller?.cancel(); // 释放流式失败保留的 writer，再交给浏览器。
                    delegateToBrowser(fallbackUrl);
                    ElementPlus.ElMessage.success('直连失败，已切换浏览器下载');
                    return;
                }
                if (state) updateDownloadTask(currentTaskId, { downloadedBytes: state.downloadedBytes, progress: state.progress });
                failDownloadTask(currentTaskId, error?.message || '下载失败');
                ElementPlus.ElMessage.error('下载失败: ' + (error?.message || ''));
            };
            const runAttempt = async (action) => {
                if (!isCurrent()) return;
                const attempt = ++attemptId;
                updateDownloadTask(currentTaskId, {
                    status: 'running', step: '正在下载大文件', error: '', speedBytesPerSecond: 0,
                    resumed: action !== 'start'
                });
                try {
                    await controller[action === 'retry' ? 'resume' : action]();
                    if (attempt !== attemptId || !isCurrent()) return;
                    const state = controller.getState();
                    if (state.status === 'paused') {
                        markPausedDownloadTask(currentTaskId, state);
                    } else if (state.status === 'cancelled') {
                        markCancelledDownloadTask(currentTaskId);
                    } else if (state.status === 'completed') {
                        completeDownloadTask(currentTaskId, {
                            step: '保存完成', downloadedBytes: state.downloadedBytes ?? file.size ?? 0
                        });
                        ElementPlus.ElMessage.success('下载完成');
                    } else if (state.status === 'failed' && action === 'retry') {
                        // 非临时故障的 resume 不启动；引擎此时已释放旧 writer。
                        await restart();
                    }
                } catch (error) {
                    if (attempt === attemptId) handleFailure(error);
                }
            };
            const restart = async () => {
                if (!isCurrent()) return;
                updateDownloadTask(currentTaskId, {
                    status: 'running', step: '正在申请下载地址', error: '', speedBytesPerSecond: 0
                });
                try {
                    await refreshUrl();
                    if (!isCurrent() || getDownloadTask(currentTaskId).status === 'cancelled') return;
                    await runChunkedFileDownload(file, latestSession, currentTaskId, fileHandle, { bucket, ownerSession });
                } catch (error) {
                    if (getDownloadTask(currentTaskId)?.status !== 'cancelled') handleFailure(error);
                }
            };
            updateDownloadTask(currentTaskId, {
                status: 'running', step: '正在下载大文件', progress: 0, downloadedBytes: 0,
                totalBytes: file.size || 0, speedBytesPerSecond: 0, error: '', resumed: false,
                controller: null, abortController: null, resumeHandler: null, retryHandler: restart, runKey,
                restartHandler: buildChunkedRestartHandler({ fileName: file.fileName, objectName, size: file.size }, currentTaskId, { bucket, ownerSession })
            });
            try {
                const nativeUrl = session.fallbackUrl || session.downloadUrl;
                if (!writesToDisk && canUseNativeDownload(nativeUrl)) {
                    delegateToBrowser(nativeUrl);
                    ElementPlus.ElMessage.success('已交给浏览器下载');
                    return;
                }
                controller = downloadHelpers.createChunkedDownloader({
                    fileHandle,
                    url: session.downloadUrl,
                    fileName: file.fileName,
                    totalSize: file.size || 0,
                    chunkSize: writesToDisk ? downloadHelpers.STREAM_SEGMENT_SIZE : downloadHelpers.DEFAULT_CHUNK_SIZE,
                    refreshUrl,
                    fetchImpl: (url, options) => {
                        requireTransferSession(ownerSession);
                        const headers = new Headers(options.headers);
                        headers.delete('Authorization');
                        Object.entries(buildDownloadHeadersForUrl(url)).forEach(([name, value]) => headers.set(name, value));
                        return fetch(url, { ...options, headers });
                    },
                    onProgress: (progress) => {
                        if (!isCurrent()) return;
                        updateDownloadTask(currentTaskId, {
                            progress: progress.progress,
                            downloadedBytes: progress.downloadedBytes,
                            totalBytes: progress.totalBytes,
                            speedBytesPerSecond: progress.speedBytesPerSecond
                        });
                    }
                });
                updateDownloadTask(currentTaskId, {
                    controller,
                    resumeHandler: () => runAttempt('resume'),
                    retryHandler: () => runAttempt('retry')
                });
                await runAttempt('start');
            } catch (error) {
                handleFailure(error);
            }
        };

        const resolveArchiveTaskStep = (task) => {
            const status = String(task?.status || '').toUpperCase();
            const stage = String(task?.stage || '').toUpperCase();
            if (status === 'COMPLETED') {
                return downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[2] : '开始下载';
            }
            if (status === 'FAILED') {
                return '归档失败';
            }
            if (status === 'EXPIRED') {
                return '归档已过期';
            }
            if (stage === 'QUEUED') {
                return '排队中';
            }
            if (stage === 'ARCHIVING') {
                return downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[1] : '等待归档完成';
            }
            return downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[1] : '等待归档完成';
        };

        const resolveArchiveTaskProgress = (task) => {
            const status = String(task?.status || '').toUpperCase();
            if (status === 'COMPLETED') {
                return 98;
            }
            const total = Number(task?.fileCountTotal || 0);
            const processed = Number(task?.fileCountProcessed || 0);
            if (total > 0) {
                return Math.max(5, Math.min(95, 5 + (processed / total) * 90));
            }
            return 10;
        };

        const createAbortablePollWait = (abortController) => (delayMs) => new Promise((resolve, reject) => {
            if (abortController.signal.aborted) {
                reject(new Error('ARCHIVE_TASK_CANCELLED'));
                return;
            }
            const timer = setTimeout(() => {
                abortController.signal.removeEventListener('abort', onAbort);
                resolve();
            }, Math.max(0, Number(delayMs || 0)));
            const onAbort = () => {
                clearTimeout(timer);
                reject(new Error('ARCHIVE_TASK_CANCELLED'));
            };
            abortController.signal.addEventListener('abort', onAbort, { once: true });
        });

        const runBatchDownloadTask = async (objectNames, fileName, taskId, bucket = currentBucket.value, session = transferSession) => {
            if (!isCurrentTransferSession(session)) return;
            const currentTaskId = taskId || addDownloadTask(createDownloadTask({
                type: 'batch-download',
                name: fileName,
                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[0] : '创建归档任务'
            })).id;
            const abortController = new AbortController();
            const requestConfig = { transferSession: session, signal: abortController.signal };
            const restartHandler = () => runBatchDownloadTask(objectNames, fileName, currentTaskId, bucket, session);
            updateDownloadTask(currentTaskId, {
                type: 'batch-download',
                name: fileName,
                status: 'running',
                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[0] : '创建归档任务',
                progress: 0,
                downloadedBytes: 0,
                totalBytes: 0,
                speedBytesPerSecond: 0,
                error: '',
                controller: null,
                abortController,
                retryHandler: restartHandler,
                restartHandler
            });
            try {
                const result = await downloadHelpers.runArchiveTaskDownload({
                    payload: {
                        scene: 'FILE',
                        taskType: 'BATCH',
                        bucket,
                        objectNames: objectNames.slice()
                    },
                    createTaskRequest: (payload) => api.post('/transfer/archive-tasks', payload, requestConfig),
                    getTaskRequest: (archiveTaskId) => api.get('/transfer/archive-tasks/' + archiveTaskId, requestConfig),
                    onUpdate: (task) => {
                        if (abortController.signal.aborted) return;
                        updateDownloadTask(currentTaskId, {
                            step: resolveArchiveTaskStep(task),
                            progress: resolveArchiveTaskProgress(task),
                            downloadedBytes: Number(task?.bytesWritten || 0),
                            totalBytes: Number(task?.bytesWritten || 0),
                            speedBytesPerSecond: 0
                        });
                    },
                    wait: createAbortablePollWait(abortController),
                    openDownload: (url) => {
                        requireTransferSession(session);
                        if (!abortController.signal.aborted) return transferUiHelpers.runArchiveDownload(url, fileName, openNativeDownload);
                    }
                });
                if (!isCurrentTransferSession(session) || abortController.signal.aborted) return;
                completeDownloadTask(currentTaskId, {
                    step: '保存完成',
                    downloadedBytes: Number(result?.task?.bytesWritten || 0),
                    totalBytes: Number(result?.task?.bytesWritten || 0)
                });
                ElementPlus.ElMessage.success('下载完成');
            } catch (error) {
                if (!isCurrentTransferSession(session) || getDownloadTask(currentTaskId)?.abortController !== abortController) return;
                if (
                    abortController.signal.aborted
                    || error?.code === 'ERR_CANCELED'
                    || error?.name === 'CanceledError'
                    || error?.message === 'ARCHIVE_TASK_CANCELLED'
                ) {
                    markCancelledDownloadTask(currentTaskId);
                    return;
                }
                const message = error.response?.data?.message || error.message || '下载失败';
                failDownloadTask(currentTaskId, message);
                ElementPlus.ElMessage.error('下载失败: ' + message);
            }
        };

        const runFolderDownloadTask = async (file, taskId, bucket = currentBucket.value, session = transferSession) => {
            if (!isCurrentTransferSession(session)) return;
            const archiveName = (file?.fileName || '目录') + '.zip';
            const currentTaskId = taskId || addDownloadTask(createDownloadTask({
                type: 'folder-download',
                name: archiveName,
                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[0] : '创建归档任务'
            })).id;
            const abortController = new AbortController();
            const requestConfig = { transferSession: session, signal: abortController.signal };
            const restartHandler = () => runFolderDownloadTask(file, currentTaskId, bucket, session);
            updateDownloadTask(currentTaskId, {
                type: 'folder-download',
                name: archiveName,
                status: 'running',
                step: downloadHelpers.BATCH_DOWNLOAD_STEPS ? downloadHelpers.BATCH_DOWNLOAD_STEPS[0] : '创建归档任务',
                progress: 0,
                downloadedBytes: 0,
                totalBytes: 0,
                speedBytesPerSecond: 0,
                error: '',
                controller: null,
                abortController,
                retryHandler: restartHandler,
                restartHandler
            });
            try {
                const result = await downloadHelpers.runArchiveTaskDownload({
                    payload: {
                        scene: 'FILE',
                        taskType: 'FOLDER',
                        bucket,
                        objectName: file.objectName
                    },
                    createTaskRequest: (payload) => api.post('/transfer/archive-tasks', payload, requestConfig),
                    getTaskRequest: (archiveTaskId) => api.get('/transfer/archive-tasks/' + archiveTaskId, requestConfig),
                    onUpdate: (task) => {
                        if (abortController.signal.aborted) return;
                        updateDownloadTask(currentTaskId, {
                            step: resolveArchiveTaskStep(task),
                            progress: resolveArchiveTaskProgress(task),
                            downloadedBytes: Number(task?.bytesWritten || 0),
                            totalBytes: Number(task?.bytesWritten || 0),
                            speedBytesPerSecond: 0
                        });
                    },
                    wait: createAbortablePollWait(abortController),
                    openDownload: (url) => {
                        requireTransferSession(session);
                        if (!abortController.signal.aborted) return transferUiHelpers.runArchiveDownload(url, archiveName, openNativeDownload);
                    }
                });
                if (!isCurrentTransferSession(session) || abortController.signal.aborted) return;
                completeDownloadTask(currentTaskId, {
                    step: '保存完成',
                    downloadedBytes: Number(result?.task?.bytesWritten || 0),
                    totalBytes: Number(result?.task?.bytesWritten || 0)
                });
                ElementPlus.ElMessage.success('下载完成');
            } catch (error) {
                if (!isCurrentTransferSession(session) || getDownloadTask(currentTaskId)?.abortController !== abortController) return;
                if (
                    abortController.signal.aborted
                    || error?.code === 'ERR_CANCELED'
                    || error?.name === 'CanceledError'
                    || error?.message === 'ARCHIVE_TASK_CANCELLED'
                ) {
                    markCancelledDownloadTask(currentTaskId);
                    return;
                }
                const message = error.response?.data?.message || error.message || '下载失败';
                failDownloadTask(currentTaskId, message);
                ElementPlus.ElMessage.error('下载失败: ' + message);
            }
        };

        const pauseDownloadTask = async (task) => {
            task = task && getDownloadTask(task.id);
            if (task?.status !== 'running' || !task.controller?.pause) return;
            task.controller.pause();
            const state = task.controller.getState();
            // writer.close() 已提交时引擎拒绝暂停，界面保留进行中。
            if (state.status === 'paused') markPausedDownloadTask(task.id, state);
        };

        const resumeDownloadTask = async (task) => {
            task = task && getDownloadTask(task.id);
            if (task?.status === 'paused') await task.resumeHandler?.();
        };

        const cancelDownloadTask = async (task) => {
            task = task && getDownloadTask(task.id);
            if (!task || !['running', 'paused', 'failed'].includes(task.status)) return;
            if (task.controller?.cancel) {
                task.controller.cancel();
                if (task.controller.getState().status === 'cancelled') markCancelledDownloadTask(task.id);
            } else if (task.abortController) {
                task.abortController.abort();
                markCancelledDownloadTask(task.id);
            }
        };

        const retryDownloadTask = async (task) => {
            task = task && getDownloadTask(task.id);
            if (task?.status === 'failed') await task.retryHandler?.();
            if (task?.status === 'completed' || task?.status === 'cancelled') await task.restartHandler?.();
        };

        const handleTransferTaskAction = async (task, action) => {
            if (!task || !['pause', 'resume', 'retry', 'cancel'].includes(action)) return;
            const { direction } = task;
            const source = direction === 'upload' ? transferUploadTasks.value : downloadTasks.value;
            const row = source.find(item => direction + ':' + item.id === task.id);
            const capability = 'can' + action[0].toUpperCase() + action.slice(1);
            if (!row || !normalizeTransferTask(row, direction)[capability]) return;
            if (direction === 'download') {
                await { pause: pauseDownloadTask, resume: resumeDownloadTask, retry: retryDownloadTask, cancel: cancelDownloadTask }[action](row);
                return;
            }
            const controller = multipartControllers.get(row.id);
            if (!controller || row.cleanupPending) return;
            if (action === 'cancel') {
                row.cleanupPending = true;
                row.cleanupFailed = false;
                row.error = '';
                try {
                    await controller.cancel();
                    if (controller.getState().status === 'cancelled') {
                        multipartControllers.delete(row.id);
                        if (transferUploadTasks.value.some(item => item.id === row.id)) multipartTargetKeys.delete(row.targetKey);
                    }
                } catch (error) {
                    if (controller.getState().status === 'cancelled') {
                        row.cleanupFailed = true;
                        row.error = '取消上传失败：' + (error?.message || '请重试');
                    }
                } finally {
                    row.cleanupPending = false;
                }
                return;
            }
            try {
                await controller[action === 'retry' ? 'resume' : action]();
            } catch (_) {
                // 分片引擎已通过 onProgress 上报任务状态。
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
            const menuOrder = ['workbench', 'buckets', 'files', 'shares', 'audit-logs', 'users', 'roles', 'minio-keys', 'config'];
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

        const uploadUrl = computed(() => contextPathHelpers.joinContextPath(appContextPath, '/api/file/upload'));
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

        const resetTransferSession = () => {
            transferSession++;
            multipartControllers.forEach(controller => {
                // 已退出的会话不再发认证请求。pause 会停止分片，等待服务端按原规则回收。
                controller.pause();
            });
            multipartControllers.clear();
            multipartTargetKeys.clear();
            downloadTasks.value.forEach(task => {
                task.controller?.cancel();
                task.abortController?.abort();
            });
            uploadTasks.value.forEach(task => task.xhr?.abort());
            stopSyncingRetry();
            syncingFileMeta.value = {};
            syncingFileKeys.value = new Set();
            clearTimeout(uploadDialogSelectionTimer);
            uploadDialogSelectionTimer = null;
            downloadTasks.value = [];
            transferUploadTasks.value = [];
            uploadTasks.value = [];
            pendingUploadFiles.value = [];
            uploadRef.value?.clearFiles();
            uploading.value = false;
            uploadCancelRequested.value = false;
            uploadDialogDropActive.value = false;
            showUploadDialog.value = false;
            downloadCenterVisible.value = false;
            downloadCenterMinimized.value = false;
            transferCenterVisible.value = false;
            transferCenterMinimized.value = false;
            transferCenterTab.value = 'all';
            fileLoading.value = false;
        };

        // Methods
        const finishLogin = async (data) => {
            if (token.value !== data.token) resetTransferSession();
            token.value = data.token;
            const persisted = persistStoredToken(token.value);
            if (!persisted) {
                ElementPlus.ElMessage.warning('本地存储受限，登录状态仅在当前会话有效，刷新后需重新登录');
            }
            userInfo.value = data;
            isLoggedIn.value = true;
            await loadCurrentPermissions();
            await loadPermissions();
            await loadInbox();
            await loadWorkbenchOverview();
            connectMessageSocket();
            currentMenu.value = workbenchHelpers.resolveInitialMenu(currentMenu.value, workbenchOverview.value, canAccessMenu);
            if (workbenchHelpers.shouldPreloadBucketsAfterLogin(currentMenu.value, Array.from(apiPermissionSet.value), isSuperAdmin.value)) {
                await loadBuckets();
            }
            handleMenuSelect(currentMenu.value);
        };

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
                if (res.code === 200) {
                    await finishLogin(res.data);
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

        const handleCertLogin = async () => {
            loginLoading.value = true;
            try {
                const res = await certHelpers.login();
                if (res.code === 200) {
                    await finishLogin(res.data);
                } else {
                    ElementPlus.ElMessage.error(res.message || '证书登录失败');
                }
            } catch (error) {
                console.error('[CertLogin] Error:', error);
                ElementPlus.ElMessage.error('证书登录异常');
            } finally {
                loginLoading.value = false;
            }
        };

        const logout = () => {
            resetTransferSession();
            token.value = '';
            const cleared = clearStoredToken();
            if (!cleared) {
                ElementPlus.ElMessage.warning('当前页面已退出。浏览器未能清除保存的登录状态；请在浏览器中为本站清理站点数据，尤其是在共用设备上。');
            }
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
            disconnectMessageSocket();
            messageCenterVisible.value = false;
            messageCenterTab.value = 'messages';
            showForwardDialog.value = false;
            forwardMessageTarget.value = null;
            forwardRecipientUserIds.value = [];
            feedbackForm.value = { category: 'GENERAL', title: '', body: '' };
            isLoggedIn.value = false;
            currentMenu.value = 'buckets';
            workbenchOverview.value = workbenchHelpers.normalizeOverview();
            shareTab.value = 'my-shares';
            loginForm.value = { username: '', password: '' };
        };

        const handleCommand = (command) => {
            if (command === 'logout') {
                logout();
            } else if (command === 'cert-mgmt') {
                showCertDialog.value = true;
                loadUserCertificates();
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

        const bindPassword = ref('');
        const handleBindCert = async () => {
            if (!bindPassword.value) {
                ElementPlus.ElMessage.warning('请输入密码');
                return;
            }
            try {
                const password = bindPassword.value;
                bindPassword.value = '';
                await certHelpers.bindCertificate(password);
                ElementPlus.ElMessage.success('证书绑定成功');
                bindPassword.value = '';
                loadUserCertificates();
            } catch (e) {
                ElementPlus.ElMessage.error(e.response?.data?.message || '绑定失败');
            }
        };

        const handleRevokeCert = async (id) => {
            try {
                await ElementPlus.ElMessageBox.confirm('撤销后，该设备不能再凭这张证书登录。是否继续？', '撤销客户端证书', { type: 'warning' });
                await certHelpers.revokeCertificate(id);
                ElementPlus.ElMessage.success('证书已撤销');
                loadUserCertificates();
            } catch (e) {
                if (e !== 'cancel' && e !== 'close') ElementPlus.ElMessage.error('撤销失败');
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

        const loadWorkbenchOverview = async () => {
            workbenchLoading.value = true;
            try {
                const res = await api.get('/workbench/overview');
                workbenchOverview.value = workbenchHelpers.normalizeOverview(res.data || res);
                syncWorkbenchShareReminders();
            } catch (error) {
                workbenchOverview.value = workbenchHelpers.normalizeOverview();
                ElementPlus.ElMessage.error('加载工作台失败: ' + (error.response?.data?.message || error.message || '未知错误'));
            } finally {
                workbenchLoading.value = false;
            }
        };

        const updateWorkbenchPreference = async (value) => {
            const nextValue = !!value;
            const previousValue = !!workbenchOverview.value.defaultWorkbench;
            workbenchOverview.value = {
                ...workbenchOverview.value,
                defaultWorkbench: nextValue
            };
            workbenchPreferenceSaving.value = true;
            try {
                await api.put('/workbench/preference', { defaultWorkbench: nextValue });
                ElementPlus.ElMessage.success('工作台偏好已保存');
            } catch (error) {
                workbenchOverview.value = {
                    ...workbenchOverview.value,
                    defaultWorkbench: previousValue
                };
                ElementPlus.ElMessage.error('保存失败: ' + (error.response?.data?.message || error.message || '未知错误'));
            } finally {
                workbenchPreferenceSaving.value = false;
            }
        };

        const syncWorkbenchShareReminders = () => {
            workbenchOverview.value = {
                ...workbenchOverview.value,
                shareReminders: shareBannerHelpers.deriveShareRemindersFromInbox(inboxList.value, 5)
            };
        };

        const showShareBanner = (banner, type = 'info') => {
            if (!banner || (!banner.title && !banner.content)) {
                return;
            }
            ElementPlus.ElNotification({
                title: banner.title || '分享提醒',
                message: banner.content || '',
                type,
                duration: 5000,
                position: 'top-right'
            });
        };

        const openWorkbenchDirectory = async (item) => {
            if (!item || !item.bucketName) {
                return;
            }
            currentBucket.value = item.bucketName;
            lastCompatibleBucket.value = item.bucketName;
            currentFolder.value = item.directoryPath || '';
            currentMenu.value = 'files';
            folderHistory.value = [];
            historyIndex.value = -1;
            updateBreadcrumb();
            await loadFiles();
        };

        const openWorkbenchShareReminder = async (item) => {
            const action = workbenchHelpers.resolveShareReminderAction(item);
            if (action.type === 'share') {
                window.open(buildShareUrl({ shareCode: action.shareCode }, false), '_blank');
                return;
            }
            if (action.type === 'message') {
                const message = inboxList.value.find(entry => entry && entry.id === action.messageId);
                await openInboxShare(message || { id: action.messageId });
                return;
            }
            messageCenterTab.value = 'messages';
            messageCenterVisible.value = true;
        };

        const openRenameDialog = (file) => {
            if (!guardFileOperation(file, '重命名')) {
                return;
            }
            renameState.value = {
                sourcePath: file.objectName,
                targetName: file.fileName || '',
                isFolder: !!file.isFolder,
                conflictPolicy: 'OVERWRITE'
            };
            showRenameDialog.value = true;
        };

        const submitRename = async () => {
            const validationMessage = renameHelpers.validateRenameTargetName(renameState.value.targetName);
            if (validationMessage) {
                ElementPlus.ElMessage.warning(validationMessage);
                return;
            }
            try {
                let conflictPolicy = 'OVERWRITE';
                try {
                    await api.post('/file/rename', renameHelpers.buildRenamePayload({
                        ...renameState.value,
                        conflictPolicy: 'ASK'
                    }, currentBucket.value));
                    conflictPolicy = '';
                } catch (error) {
                    const message = error.response?.data?.message || error.message || '';
                    if (String(message).includes('已存在')) {
                        const action = await ElementPlus.ElMessageBox.confirm(
                            '同目录下已存在同名项目。选择“确定”为覆盖，选择“取消”为自动重命名。',
                            '检测到同名冲突',
                            { confirmButtonText: '覆盖', cancelButtonText: '自动重命名', distinguishCancelAndClose: true, type: 'warning' }
                        ).then(() => 'overwrite').catch((action) => action === 'cancel' ? 'rename' : 'close');
                        if (action === 'close') {
                            return;
                        }
                        conflictPolicy = action === 'rename' ? 'RENAME' : 'OVERWRITE';
                    } else {
                        throw error;
                    }
                }
                if (conflictPolicy) {
                    await api.post('/file/rename', renameHelpers.buildRenamePayload({
                        ...renameState.value,
                        conflictPolicy
                    }, currentBucket.value));
                }
                ElementPlus.ElMessage.success('重命名成功');
                showRenameDialog.value = false;
                await loadFiles();
            } catch (error) {
                ElementPlus.ElMessage.error('重命名失败: ' + (error.response?.data?.message || error.message || '未知错误'));
            }
        };

        const toggleCurrentDirectoryFavorite = async () => {
            if (!currentBucket.value || !currentFolder.value) {
                ElementPlus.ElMessage.warning('根目录暂不支持收藏，请先进入具体目录');
                return;
            }
            try {
                if (isCurrentDirectoryFavorite.value) {
                    await api.delete('/workbench/favorites', {
                        params: {
                            bucketName: currentBucket.value,
                            directoryPath: currentFolder.value
                        }
                    });
                    ElementPlus.ElMessage.success('已取消收藏目录');
                } else {
                    const normalizedFolder = String(currentFolder.value || '').replace(/\/$/, '');
                    const lastSlashIndex = normalizedFolder.lastIndexOf('/');
                    const directoryName = lastSlashIndex >= 0
                        ? normalizedFolder.slice(lastSlashIndex + 1)
                        : normalizedFolder;
                    await api.post('/workbench/favorites', {
                        bucketName: currentBucket.value,
                        directoryPath: currentFolder.value,
                        directoryName: directoryName || currentFolder.value
                    });
                    ElementPlus.ElMessage.success('已收藏当前目录');
                }
                await loadWorkbenchOverview();
            } catch (error) {
                ElementPlus.ElMessage.error((isCurrentDirectoryFavorite.value ? '取消收藏失败: ' : '收藏目录失败: ')
                    + (error.response?.data?.message || error.message || '未知错误'));
            }
        };

        const removeFavoriteDirectory = async (item) => {
            if (!item || !item.bucketName || !item.directoryPath) {
                return;
            }
            try {
                await api.delete('/workbench/favorites', {
                    params: {
                        bucketName: item.bucketName,
                        directoryPath: item.directoryPath
                    }
                });
                ElementPlus.ElMessage.success('已取消收藏目录');
                await loadWorkbenchOverview();
            } catch (error) {
                ElementPlus.ElMessage.error('取消收藏失败: ' + (error.response?.data?.message || error.message || '未知错误'));
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
                    await loadWorkbenchOverview();
                    connectMessageSocket();
                    currentMenu.value = workbenchHelpers.resolveInitialMenu(currentMenu.value, workbenchOverview.value, canAccessMenu);
                    if (workbenchHelpers.shouldPreloadBucketsAfterLogin(currentMenu.value, Array.from(apiPermissionSet.value), isSuperAdmin.value)) {
                        await loadBuckets();
                    }
                    handleMenuSelect(currentMenu.value);
                } else {
                    logout();
                }
            } catch (error) {
                logout();
            }
        };

        const buildMessageSocketUrl = () => {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            return protocol + '//' + window.location.host + '/ws/messages?token=' + encodeURIComponent(token.value);
        };

        const clearMessageSocketReconnectTimer = () => {
            if (messageSocketReconnectTimer) {
                clearTimeout(messageSocketReconnectTimer);
                messageSocketReconnectTimer = null;
            }
        };

        const scheduleMessageSocketReconnect = () => {
            if (!isLoggedIn.value || !token.value) {
                return;
            }
            clearMessageSocketReconnectTimer();
            messageSocketReconnectTimer = setTimeout(() => {
                connectMessageSocket();
            }, 2000);
        };

        const handleSocketMessageEvent = async (eventName, payload) => {
            if (!eventName) {
                await loadInbox();
                return;
            }
            if (eventName === 'message.read' || eventName === 'message.deleted') {
                applyIncomingInboxMessage(payload || {});
                return;
            }
            if (eventName === 'message.created' || eventName === 'feedback.received') {
                await loadInbox();
            }
        };

        const connectMessageSocket = () => {
            if (!isLoggedIn.value || !token.value || typeof WebSocket === 'undefined') {
                return;
            }
            if (messageSocket && (
                messageSocket.readyState === WebSocket.OPEN || messageSocket.readyState === WebSocket.CONNECTING
            )) {
                return;
            }
            try {
                const socket = new WebSocket(buildMessageSocketUrl());
                messageSocket = socket;
                socket.onopen = () => {
                    clearMessageSocketReconnectTimer();
                };
                socket.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data || '{}');
                        handleSocketMessageEvent(data.event, data.payload);
                    } catch (error) {
                        loadInbox();
                    }
                };
                socket.onerror = () => {
                    if (messageSocket === socket) {
                        socket.close();
                    }
                };
                socket.onclose = () => {
                    if (messageSocket === socket) {
                        messageSocket = null;
                    }
                    scheduleMessageSocketReconnect();
                };
            } catch (error) {
                scheduleMessageSocketReconnect();
            }
        };

        const disconnectMessageSocket = () => {
            clearMessageSocketReconnectTimer();
            if (!messageSocket) {
                return;
            }
            messageSocket.onclose = null;
            messageSocket.onerror = null;
            messageSocket.onmessage = null;
            messageSocket.close();
            messageSocket = null;
        };

        const handleMenuSelect = (index) => {
            if (!ensureMenuAccess(index)) {
                return;
            }
            closeNavigation();
            currentMenu.value = index;
            if (index === 'workbench') {
                loadWorkbenchOverview();
            } else if (index === 'buckets') {
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
        const clearBucketStatsPollTimer = () => {
            if (bucketStatsPollTimer) {
                clearTimeout(bucketStatsPollTimer);
                bucketStatsPollTimer = null;
            }
        };

        const getBucketStatsStatus = async () => {
            const res = await api.get('/file/buckets/stats/status');
            return res.data || res;
        };

        const applyBucketStatsRefreshStatus = (payload) => {
            bucketStatsRefreshStatus.value = payload || { status: 'IDLE' };
            bucketStatsRefreshing.value = bucketStatsRefreshStatus.value.status === 'RUNNING';
        };

        const resolveBucketStatsStatus = (bucket) => (bucket && bucket.statsStatus) ? bucket.statsStatus : 'NOT_CALCULATED';

        const syncBucketStatsRefreshStatus = async () => {
            try {
                const payload = await getBucketStatsStatus();
                applyBucketStatsRefreshStatus(payload);
                if (payload && payload.status === 'RUNNING') {
                    clearBucketStatsPollTimer();
                    bucketStatsPollTimer = setTimeout(pollBucketStatsStatus, 1000);
                }
            } catch (error) {
                bucketStatsRefreshing.value = false;
            }
        };

        const pollBucketStatsStatus = async () => {
            clearBucketStatsPollTimer();
            try {
                const payload = await getBucketStatsStatus();
                applyBucketStatsRefreshStatus(payload);
                if (payload && payload.status === 'RUNNING') {
                    bucketStatsPollTimer = setTimeout(pollBucketStatsStatus, 1000);
                    return;
                }
                await loadBuckets();
            } catch (error) {
                bucketStatsRefreshing.value = false;
                ElementPlus.ElMessage.error('获取桶统计状态失败');
            }
        };

        const loadBuckets = async () => {
            bucketLoading.value = true;
            try {
                const res = await api.get('/file/buckets', {
                    params: { sortBy: bucketSortBy.value, order: bucketOrder.value }
                });
                if (res.code === 200) {
                    bucketList.value = res.data || [];
                }
                await syncBucketStatsRefreshStatus();
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

        const refreshBucketStats = async () => {
            bucketStatsRefreshing.value = true;
            try {
                const res = await api.post('/file/buckets/stats/refresh');
                const payload = res.data || res;
                ElementPlus.ElMessage.success(payload?.message || '桶统计刷新任务已启动');
                await pollBucketStatsStatus();
            } catch (error) {
                bucketStatsRefreshing.value = false;
                ElementPlus.ElMessage.error('刷新桶统计失败');
            }
        };

        // File management
        let fileListAppliedRequest = 0;
        const loadFiles = async ({ silent = false } = {}) => {
            if (!currentBucket.value) return;
            const request = ++fileListRequest;
            const session = transferSession;
            const menu = currentMenu.value;
            const params = {
                folder: currentFolder.value,
                bucket: currentBucket.value,
                sortBy: fileSortBy.value,
                order: fileOrder.value,
                folderFirst: folderFirstMode.value,
                page: filePage.value,
                size: filePageSize.value
            };
            const isCurrentView = () => isCurrentTransferSession(session)
                && request >= visibleFileListRequest && request > fileListAppliedRequest
                && menu === currentMenu.value && params.bucket === currentBucket.value && params.folder === currentFolder.value
                && params.sortBy === fileSortBy.value && params.order === fileOrder.value && params.folderFirst === folderFirstMode.value
                && params.page === filePage.value && params.size === filePageSize.value;
            if (!silent) {
                visibleFileListRequest = request;
                updateBreadcrumb();
                fileLoading.value = true;
            }
            try {
                const res = await api.get('/file/list', { params, transferSession: session });
                if (res.code === 200 && isCurrentView()) {
                    fileListAppliedRequest = request;
                    // 判断返回的是分页结果还是普通列表
                    if (res.data.items !== undefined) {
                        // 分页模式
                        fileList.value = decorateFileRows(res.data.items || []);
                        fileTotal.value = res.data.total || 0;
                    } else {
                        // 非分页模式（兼容旧版）
                        fileList.value = decorateFileRows(res.data || []);
                        fileTotal.value = res.data ? res.data.length : 0;
                    }
                }
            } catch (error) {
                if (!silent && isCurrentView()) ElementPlus.ElMessage.error('加载文件列表失败');
            } finally {
                if (!silent && request === visibleFileListRequest) {
                    fileLoading.value = false;
                }
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
            const nextSortState = fileListUiHelpers.resolveFileSortState({ prop, order });
            fileSortBy.value = nextSortState.sortBy;
            fileOrder.value = nextSortState.order;
            folderFirstMode.value = nextSortState.folderFirst;
            filePage.value = 1;
            loadFiles();
        };

        const refreshFiles = () => {
            loadFiles();
        };

        const loadUploadConfig = async (session = transferSession) => {
            if (!isCurrentTransferSession(session)) return;
            try {
                const res = await api.get('/file/upload-config', { transferSession: session });
                if (!isCurrentTransferSession(session)) return;
                if (res.code === 200) {
                    const extensions = res.data.allowedExtensions || [];
                    fileConfig.value.allowedExtensionsText = extensions.join(',');
                    if (res.data.maxFileSize === -1) {
                        fileConfig.value.maxFileSizeMB = -1;
                    } else {
                        fileConfig.value.maxFileSizeMB = Math.floor((res.data.maxFileSize || 104857600) / 1024 / 1024);
                    }
                    fileConfig.value.fileDropMode = res.data.fileDropMode || fileConfig.value.fileDropMode || 'INSTANT';
                    fileConfig.value.uploadDialogMode = res.data.uploadDialogMode || fileConfig.value.uploadDialogMode || 'INSTANT';
                }
            } catch (error) {
                if (isCurrentTransferSession(session)) ElementPlus.ElMessage.error('加载上传配置失败');
            }
        };

        // 打开上传对话框时加载配置
        const openUploadDialog = () => {
            loadUploadConfig();
            uploadDialogDropActive.value = false;
            pendingUploadFiles.value = [];
            if (uploadRef.value) {
                uploadRef.value.clearFiles();
            }
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

        const canSelectFileRow = (row) => {
            return fileListUiHelpers.canOperateFile(normalizeFileRow(row), syncingFileKeys.value);
        };

        const guardFileOperation = (file, actionLabel) => {
            if (canSelectFileRow(file)) {
                return true;
            }
            ElementPlus.ElMessage.warning((actionLabel || '当前操作') + '暂不可用，文件信息同步中');
            return false;
        };

        const handleFileNameClick = (file) => {
            if (file?.isFolder && !guardFileOperation(file, '打开目录')) {
                return;
            }
            if (file.isFolder) {
                navigateToFolder(file.objectName);
                return;
            }
            previewFile(file);
        };

        const handleFileDragOver = (event) => {
            if (!currentBucket.value || uploading.value) {
                return;
            }
            if (event?.dataTransfer) {
                event.dataTransfer.dropEffect = 'copy';
            }
            fileDropActive.value = true;
        };

        const handleFileDragEnter = (event) => {
            if (!currentBucket.value || uploading.value) {
                return;
            }
            if (event?.dataTransfer) {
                event.dataTransfer.dropEffect = 'copy';
            }
            if (fileDropDragDepth <= 0) {
                fileDropDragDepth = 0;
            }
            fileDropDragDepth += 1;
            fileDropActive.value = true;
        };

        const handleFileDragLeave = (event) => {
            const relatedTarget = event?.relatedTarget;
            if (event?.currentTarget && relatedTarget && event.currentTarget.contains(relatedTarget)) {
                return;
            }
            fileDropDragDepth = Math.max(0, fileDropDragDepth - 1);
            if (fileDropDragDepth === 0) {
                fileDropActive.value = false;
            }
        };

        const handleFileDrop = async (event) => {
            const target = { bucket: currentBucket.value, folder: currentFolder.value, session: transferSession };
            const menu = currentMenu.value;
            fileDropDragDepth = 0;
            fileDropActive.value = false;
            if (!target.bucket) {
                ElementPlus.ElMessage.warning('请先选择存储桶');
                return;
            }
            if (!hasApiPermission('API_FILE_OPERATE') || !hasBucketPermission(target.bucket, 'UPLOAD')) {
                ElementPlus.ElMessage.warning('当前账号无上传权限');
                return;
            }
            const droppedFiles = Array.from(event?.dataTransfer?.files || []).filter(Boolean);
            if (!droppedFiles.length) return;
            await loadUploadConfig(target.session);
            if (!isCurrentTransferSession(target.session)) return;
            const sameView = currentMenu.value === menu && currentBucket.value === target.bucket && currentFolder.value === target.folder;
            if (fileConfig.value.fileDropMode === 'MANUAL') {
                if (!sameView) {
                    ElementPlus.ElMessage.warning('目录已切换，请重新拖放文件');
                    return;
                }
                showUploadDialog.value = true;
                pendingUploadFiles.value = pendingUploadFiles.value.concat(normalizePendingUploadFiles(droppedFiles));
                return;
            }
            if (sameView) showUploadDialog.value = true;
            await runUploadBatch(droppedFiles, { ...target, closeDialog: false, closeMultipartDialog: sameView });
        };

        const handleUploadDialogDragOver = (event) => {
            if (!currentBucket.value || uploading.value) {
                return;
            }
            if (event?.dataTransfer) {
                event.dataTransfer.dropEffect = 'copy';
            }
            uploadDialogDropActive.value = true;
        };

        const handleUploadDialogDragLeave = (event) => {
            const relatedTarget = event?.relatedTarget;
            if (event?.currentTarget && relatedTarget && event.currentTarget.contains(relatedTarget)) {
                return;
            }
            uploadDialogDropActive.value = false;
        };

        const queueDialogUploadFiles = async (files) => {
            const rawFiles = (files || []).filter(Boolean);
            if (!rawFiles.length) {
                return;
            }
            if (!currentBucket.value) {
                ElementPlus.ElMessage.warning('请先选择存储桶');
                return;
            }
            if (uploading.value) {
                ElementPlus.ElMessage.warning('已有上传任务进行中');
                return;
            }
            showUploadDialog.value = true;
            await runUploadBatch(rawFiles, { closeDialog: false });
        };

        const normalizePendingUploadFiles = (files) => uploadDialogHelpers.normalizePendingUploadFiles(files);

        const applyUploadBatchRenameRule = () => {
            pendingUploadFiles.value = uploadDialogHelpers.applyBatchRename(
                pendingUploadFiles.value,
                uploadBatchRenameRule.value
            );
        };

        const syncPendingUploadFilesFromUploadRef = () => {
            pendingUploadFiles.value = normalizePendingUploadFiles(uploadRef.value?.uploadFiles || []);
        };

        const handleUploadDialogDrop = async (event) => {
            uploadDialogDropActive.value = false;
            const droppedFiles = Array.from(event?.dataTransfer?.files || []).filter(Boolean);
            if (!droppedFiles.length) {
                return;
            }
            if (uploadRef.value) {
                uploadRef.value.clearFiles();
            }
            if (fileConfig.value.uploadDialogMode === 'MANUAL') {
                pendingUploadFiles.value = pendingUploadFiles.value.concat(normalizePendingUploadFiles(droppedFiles));
                showUploadDialog.value = true;
                return;
            }
            await queueDialogUploadFiles(droppedFiles);
        };

        const downloadFile = async (file) => {
            if (!guardFileOperation(file, '下载文件')) {
                return;
            }
            // 目录下载
            if (file.isFolder) {
                await runFolderDownloadTask(file);
                return;
            }

            // 文件下载：保存对话框打开期间仍可能导航或切换账号。
            const bucket = currentBucket.value;
            const ownerSession = transferSession;
            const objectName = file.objectName;
            try {
                const useTaskCenter = shouldUseTaskCenterForFile(file);
                const fileHandle = useTaskCenter && downloadHelpers.supportsStreamToDisk?.()
                    ? await window.showSaveFilePicker({ suggestedName: file.fileName || 'download.bin' }) : null;
                const session = await requestFileDownloadSession(objectName, bucket, ownerSession);
                recordClientDiagnostic('info', 'download-session', {
                    fileName: file.fileName,
                    bucket,
                    objectName,
                    mode: session.mode,
                    downloadUrl: session.downloadUrl,
                    fallbackUrl: session.fallbackUrl
                });
                if (useTaskCenter) {
                    await runChunkedFileDownload(file, session, null, fileHandle, { bucket, ownerSession });
                    return;
                }
                await transferUiHelpers.runSingleFileDownload(session, (url) => openNativeDownload(url));
            } catch (error) {
                if (!isCurrentTransferSession(ownerSession) || error?.name === 'AbortError') return;
                ElementPlus.ElMessage.error('下载失败: ' + (error.response?.data?.message || error.message || '未知错误'));
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

        const copyPreviewText = async () => {
            if (!previewText.value) {
                ElementPlus.ElMessage.warning('当前没有可复制的文本内容');
                return;
            }
            try {
                await copyText(previewText.value);
                ElementPlus.ElMessage.success('文本已复制到剪贴板');
            } catch (error) {
                ElementPlus.ElMessage.error('复制失败: ' + error.message);
            }
        };

        const previewFile = async (file) => {
            if (!guardFileOperation(file, '预览文件')) {
                return;
            }
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
            return urlHelpers.buildShareUrl(window.location.origin, appContextPath, share, includePassword);
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

        const formatShareTargetType = (share) => shareManagementHelpers.formatShareTargetType(share);

        const deleteFile = async (file) => {
            if (!guardFileOperation(file, '删除文件')) {
                return;
            }
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

        const getFileRowActions = (file) => {
            return fileActionVisibilityHelpers.splitRowActions([
                buildRowActionDefinition('DOWNLOAD', {
                    visible: hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'DOWNLOAD'),
                    disabled: !!file.syncing,
                    onClick: () => downloadFile(file)
                }),
                buildRowActionDefinition('SHARE', {
                    visible: hasApiPermission('API_SHARE_MANAGE') && hasBucketPermission(currentBucket.value, 'SHARE'),
                    disabled: !!file.syncing,
                    onClick: () => shareFile(file)
                }),
                buildRowActionDefinition('PREVIEW', {
                    visible: !file.isFolder && hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'DOWNLOAD'),
                    disabled: !!file.syncing,
                    onClick: () => previewFile(file)
                }),
                buildRowActionDefinition('RENAME', {
                    visible: hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'BATCH'),
                    disabled: !!file.syncing,
                    onClick: () => openRenameDialog(file)
                }),
                buildRowActionDefinition('DELETE', {
                    type: 'danger',
                    visible: hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'DELETE'),
                    disabled: !!file.syncing,
                    onClick: () => deleteFile(file)
                }),
                buildRowActionDefinition('MOVE', {
                    visible: hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'BATCH'),
                    disabled: !!file.syncing,
                    onClick: () => openTransferDialog('MOVE', [file])
                }),
                buildRowActionDefinition('COPY', {
                    visible: hasApiPermission('API_FILE_OPERATE') && hasBucketPermission(currentBucket.value, 'BATCH'),
                    disabled: !!file.syncing,
                    onClick: () => openTransferDialog('COPY', [file])
                })
            ], runtimeConfig.value.fileRowHiddenActions || fileConfig.value.fileRowHiddenActions || ['DELETE', 'MOVE', 'COPY']);
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

        const batchMove = async () => {
            if (selectedFiles.value.length === 0) {
                ElementPlus.ElMessage.warning('请先选择要移动的文件');
                return;
            }
            openTransferDialog('MOVE', selectedFiles.value);
        };

        const batchCopy = async () => {
            if (selectedFiles.value.length === 0) {
                ElementPlus.ElMessage.warning('请先选择要复制的文件');
                return;
            }
            openTransferDialog('COPY', selectedFiles.value);
        };

        const loadTransferFolderOptions = async (folder) => {
            const normalizedFolder = fileTransferHelpers.normalizeTransferTargetFolder(folder);
            const res = await api.get('/file/list', {
                params: {
                    folder: normalizedFolder,
                    bucket: currentBucket.value,
                    sortBy: 'name',
                    order: 'asc'
                }
            });
            if (res.code !== 200) {
                throw new Error(res.message || '加载目录失败');
            }
            const rows = Array.isArray(res.data) ? res.data : (res.data?.items || []);
            transferState.value.folderOptions = fileTransferHelpers.buildTransferFolderOptions(rows, normalizedFolder);
            transferState.value.currentFolder = normalizedFolder;
            transferState.value.targetFolder = normalizedFolder;
        };

        const openTransferDialog = async (operation, sources) => {
            if (!currentBucket.value) {
                ElementPlus.ElMessage.warning('请先选择存储桶');
                return;
            }
            const initialFolder = currentFolder.value || '';
            transferState.value = {
                operation,
                sources: (sources || []).map(item => ({
                    fileName: item.fileName,
                    objectName: item.objectName,
                    isFolder: !!item.isFolder
                })),
                targetFolder: initialFolder,
                folderStack: [initialFolder],
                currentFolder: initialFolder,
                folderOptions: []
            };
            showTransferDialog.value = true;
            await loadTransferFolderOptions(initialFolder);
        };

        const enterTransferFolder = async (folder) => {
            const nextFolder = fileTransferHelpers.normalizeTransferTargetFolder(folder?.objectName || '');
            transferState.value.folderStack = [nextFolder];
            await loadTransferFolderOptions(nextFolder);
        };

        const backTransferFolder = async () => {
            const parentFolder = fileTransferHelpers.resolveParentTransferFolder(transferState.value.currentFolder);
            transferState.value.folderStack = [parentFolder];
            await loadTransferFolderOptions(parentFolder);
        };

        const confirmTransfer = async () => {
            try {
                const previewRes = await api.post('/file/transfer/preview', {
                    operation: transferState.value.operation,
                    bucket: currentBucket.value,
                    sources: transferState.value.sources.map(item => item.objectName),
                    targetFolder: transferState.value.targetFolder,
                    conflictPolicy: 'ASK'
                });
                const preview = previewRes.data || {};
                if (preview.valid === false) {
                    ElementPlus.ElMessage.warning(preview.message || '目标目录不可用');
                    return;
                }
                let conflictPolicy = 'OVERWRITE';
                if (preview.hasConflicts) {
                    const action = await ElementPlus.ElMessageBox.confirm(
                        '目标目录存在同名文件或目录。选择“确定”为覆盖，选择“取消”为继续选择是否共存。',
                        '检测到同名冲突',
                        { confirmButtonText: '覆盖', cancelButtonText: '共存', distinguishCancelAndClose: true, type: 'warning' }
                    ).then(() => 'overwrite').catch((action) => action === 'cancel' ? 'rename' : 'close');
                    if (action === 'close') {
                        return;
                    }
                    conflictPolicy = fileTransferHelpers.resolveConflictPolicy(action);
                }
                const executeRes = await api.post('/file/transfer/execute', {
                    operation: transferState.value.operation,
                    bucket: currentBucket.value,
                    sources: transferState.value.sources.map(item => item.objectName),
                    targetFolder: transferState.value.targetFolder,
                    conflictPolicy: conflictPolicy || 'OVERWRITE'
                });
                if (executeRes.code === 200) {
                    ElementPlus.ElMessage.success((transferState.value.operation === 'MOVE' ? '移动' : '复制') + '完成');
                    showTransferDialog.value = false;
                    await loadFiles();
                }
            } catch (error) {
                if (error !== 'close') {
                    ElementPlus.ElMessage.error((transferState.value.operation === 'MOVE' ? '移动' : '复制') + '失败: ' + (error.response?.data?.message || error.message || '未知错误'));
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
            if (!guardFileOperation(file, '分享文件')) {
                return;
            }
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
                    const recipientNames = shareForm.value.targetType === 'USER'
                        ? (recipientList.value || [])
                            .filter(item => (shareForm.value.recipientUserIds || []).includes(item.id))
                            .map(item => item.nickname || item.username)
                        : [];
                    showShareBanner(shareBannerHelpers.buildShareSentBanner({
                        targetType: shareForm.value.targetType,
                        fileName: res.data?.fileName || shareForm.value.objectName.split('/').filter(Boolean).pop() || '当前项目',
                        hasPassword: !!res.data?.hasPassword,
                        recipientNames
                    }), 'success');
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
            selectedFiles.value = (selection || []).filter(item => canSelectFileRow(item));
        };

        const handleUploadDialogChange = (file, fileList) => {
            if (fileConfig.value.uploadDialogMode === 'MANUAL') {
                pendingUploadFiles.value = uploadDialogHelpers.mergePendingUploadFiles(
                    pendingUploadFiles.value,
                    Array.isArray(fileList) && fileList.length ? fileList : [file]
                );
                return;
            }
            if (uploadDialogSelectionTimer) {
                clearTimeout(uploadDialogSelectionTimer);
            }
            uploadDialogSelectionTimer = setTimeout(async () => {
                uploadDialogSelectionTimer = null;
                const uploadFiles = Array.isArray(fileList) && fileList.length
                    ? fileList
                    : (uploadRef.value?.uploadFiles || []);
                const rawFiles = uploadFiles
                    .map(item => item.raw)
                    .filter(Boolean);
                if (uploadRef.value) {
                    uploadRef.value.clearFiles();
                }
                await queueDialogUploadFiles(rawFiles);
            }, 0);
        };

        const removePendingUploadFile = (fileId) => {
            pendingUploadFiles.value = pendingUploadFiles.value.filter(item => item.id !== fileId);
            if (uploadRef.value) {
                const target = (uploadRef.value.uploadFiles || []).find(item => String(item.uid) === String(fileId));
                if (target) {
                    uploadRef.value.handleRemove(target);
                }
            }
        };

        const submitUpload = async () => {
            const uploadItems = pendingUploadFiles.value
                .map(item => ({
                    ...item,
                    targetName: String(item.targetName || '').trim()
                }))
                .filter(item => item.raw);
            if (!uploadItems.length) {
                ElementPlus.ElMessage.warning('请选择需要上传的文件');
                return;
            }
            const invalidItem = uploadItems.find(item => !item.targetName);
            if (invalidItem) {
                ElementPlus.ElMessage.warning('存在未填写目标文件名的上传项');
                return;
            }
            pendingUploadFiles.value = [];
            if (uploadRef.value) {
                uploadRef.value.clearFiles();
            }
            await queueDialogUploadFiles(uploadItems);
        };

        const cancelUpload = async () => {
            if (uploading.value) {
                try {
                    await ElementPlus.ElMessageBox.confirm(
                        '是否取消上传，这将终止进行中的上传任务和取消还未进行的任务',
                        '取消上传',
                        { confirmButtonText: '确定取消', cancelButtonText: '继续上传', type: 'warning' }
                    );
                    uploadCancelRequested.value = true;
                    uploadTasks.value.forEach(task => {
                        if (task.status === 'running' && task.xhr) {
                            task.xhr.abort();
                        }
                    });
                    uploading.value = false;
                    showUploadDialog.value = false;
                    pendingUploadFiles.value = [];
                    if (uploadRef.value) {
                        uploadRef.value.clearFiles();
                    }
                } catch (e) {
                    // 用户点击继续上传，不做任何操作
                }
            } else {
                showUploadDialog.value = false;
                pendingUploadFiles.value = [];
                uploadTasks.value = uploadDialogHelpers.clearClosedUploadTasks(uploadTasks.value);
                if (uploadRef.value) {
                    uploadRef.value.clearFiles();
                }
                uploadDialogDropActive.value = false;
            }
        };

        // Share management
        const loadShares = async () => {
            shareLoading.value = true;
            try {
                const res = await api.get('/share/list', {
                    params: sharePaginationHelpers.buildPagedShareParams(
                        sharePage.value,
                        sharePageSize.value,
                        shareValidOnly.value
                    )
                });
                if (res.code === 200) {
                    const pageData = res.data || { items: [], total: 0 };
                    shareTotal.value = pageData.total || 0;
                    shareList.value = (pageData.items || []).map(item => {
                        const contentItems = shareManagementHelpers.normalizeShareContentItems(item);
                        const visibleContent = shareManagementHelpers.buildVisibleShareContent(contentItems, 3);
                        return {
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
                            shareItems: contentItems,
                            visibleShareItems: visibleContent.visibleItems,
                            hiddenShareItemCount: visibleContent.hiddenCount,
                            hiddenShareItems: visibleContent.hiddenItems,
                            shareItemsLoaded: !item.isFolder,
                            shareItemsLoading: false,
                            shareItemsError: false,
                            shareItemsErrorMessage: ''
                        };
                    });
                    shareList.value.forEach(share => loadShareItemsForManage(share));
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载分享列表失败');
            } finally {
                shareLoading.value = false;
            }
        };

        const handleShareValidOnlyChange = () => {
            sharePage.value = sharePaginationHelpers.resolvePageAfterFilterChange(sharePage.value);
            loadShares();
        };

        const handleShareSizeChange = (val) => {
            sharePageSize.value = val;
            sharePage.value = 1;
            loadShares();
        };

        const handleSharePageChange = (val) => {
            sharePage.value = val;
            loadShares();
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

        const formatInboxMessage = (message) => {
            if (!message) {
                return message;
            }
            const shareMessage = shareBannerHelpers.isShareMessage(message);
            const contentItems = shareMessage
                ? shareManagementHelpers.normalizeShareContentItems(message, message.shareItems || [])
                : [];
            const visibleContent = shareManagementHelpers.buildVisibleShareContent(contentItems, 3);
            return {
                ...message,
                isShareMessage: shareMessage,
                createTimeText: formatDateTime(message.createTime),
                readTimeText: formatDateTime(message.readTime),
                subtitle: messageCenterHelpers.buildMessageSubtitle(message),
                shareItems: contentItems,
                visibleShareItems: visibleContent.visibleItems,
                hiddenShareItemCount: visibleContent.hiddenCount,
                hiddenShareItems: visibleContent.hiddenItems,
                shareItemsErrorMessage: shareMessage && message.shareItemsError ? '加载失败：' + message.shareItemsError : ''
            };
        };

        const applyIncomingInboxMessage = (incomingMessage) => {
            if (!incomingMessage || incomingMessage.id === null || incomingMessage.id === undefined) {
                return;
            }
            const currentMessage = inboxList.value.find(item => item && item.id === incomingMessage.id);
            const mergedMessage = currentMessage
                ? { ...currentMessage, ...incomingMessage }
                : incomingMessage;
            inboxList.value = messageCenterHelpers.mergeIncomingMessage(
                inboxList.value,
                formatInboxMessage(mergedMessage)
            );
            syncWorkbenchShareReminders();
            if (!currentMessage && shareBannerHelpers.isShareMessage(mergedMessage) && !mergedMessage.read) {
                showShareBanner(shareBannerHelpers.buildShareReceivedBanner(mergedMessage), 'info');
            }
        };

        const loadInbox = async () => {
            inboxLoading.value = true;
            try {
                const res = await api.get('/messages/inbox', {
                    params: sharePaginationHelpers.buildPagedShareParams(
                        inboxPage.value,
                        inboxPageSize.value,
                        inboxValidOnly.value
                    )
                });
                if (res.code === 200) {
                    const pageData = res.data || { items: [], total: 0 };
                    inboxTotal.value = pageData.total || 0;
                    inboxList.value = (pageData.items || []).map(item => formatInboxMessage(item));
                    syncWorkbenchShareReminders();
                }
            } catch (error) {
                ElementPlus.ElMessage.error('加载收件箱失败');
            } finally {
                inboxLoading.value = false;
            }
        };

        const handleInboxValidOnlyChange = () => {
            inboxPage.value = sharePaginationHelpers.resolvePageAfterFilterChange(inboxPage.value);
            loadInbox();
        };

        const handleInboxSizeChange = (val) => {
            inboxPageSize.value = val;
            inboxPage.value = 1;
            loadInbox();
        };

        const handleInboxPageChange = (val) => {
            inboxPage.value = val;
            loadInbox();
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

        const openForwardDialogForMessage = async (message) => {
            if (!message || !message.id) {
                return;
            }
            forwardMessageTarget.value = message;
            forwardRecipientUserIds.value = [];
            showForwardDialog.value = true;
            if (!recipientList.value.length) {
                await loadRecipients();
            }
        };

        const forwardInboxMessage = async () => {
            const targetMessageId = forwardMessageTarget.value?.id;
            if (!targetMessageId) {
                return;
            }
            if (!forwardRecipientUserIds.value.length) {
                ElementPlus.ElMessage.warning('请选择至少一个接收人');
                return;
            }
            try {
                const res = await api.post('/messages/' + targetMessageId + '/forward', {
                    recipientUserIds: forwardRecipientUserIds.value
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('转发成功');
                    showForwardDialog.value = false;
                    forwardMessageTarget.value = null;
                    forwardRecipientUserIds.value = [];
                }
            } catch (error) {
                ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '转发失败');
            }
        };

        const submitFeedback = async () => {
            const payload = {
                category: (feedbackForm.value.category || '').trim() || 'GENERAL',
                title: (feedbackForm.value.title || '').trim(),
                body: (feedbackForm.value.body || '').trim()
            };
            if (!payload.body) {
                ElementPlus.ElMessage.warning('请填写反馈内容');
                return;
            }
            try {
                const res = await api.post('/messages/feedback', payload);
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('反馈已提交');
                    feedbackForm.value = { category: 'GENERAL', title: '', body: '' };
                    messageCenterTab.value = 'messages';
                }
            } catch (error) {
                ElementPlus.ElMessage.error(error.response?.data?.message || error.message || '反馈提交失败');
            }
        };

        const deleteShare = async (share) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要删除这条分享记录吗？删除记录不会取消已发出的分享。', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.delete('/share/delete', { params: { shareCode: share.shareCode } });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('删除记录成功');
                    loadShares();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('删除失败: ' + (error.response?.data?.message || error.message));
                }
            }
        };

        const cancelShare = async (share) => {
            try {
                await ElementPlus.ElMessageBox.confirm('确定要取消这个分享吗？取消后接收人和分享链接都无法继续访问。', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                });
                const res = await api.post('/share/cancel', null, { params: { shareCode: share.shareCode } });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('取消分享成功');
                    loadShares();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElementPlus.ElMessage.error('取消分享失败: ' + (error.response?.data?.message || error.message));
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
                    const contentItems = shareManagementHelpers.normalizeShareContentItems(share, res.data || []);
                    const visibleContent = shareManagementHelpers.buildVisibleShareContent(contentItems, 3);
                    share.shareItems = contentItems;
                    share.visibleShareItems = visibleContent.visibleItems;
                    share.hiddenShareItemCount = visibleContent.hiddenCount;
                    share.hiddenShareItems = visibleContent.hiddenItems;
                    share.shareItemsLoaded = true;
                    share.shareItemsError = false;
                    share.shareItemsErrorMessage = '';
                }
            } catch (error) {
                share.shareItems = [];
                share.visibleShareItems = [];
                share.hiddenShareItemCount = 0;
                share.hiddenShareItems = [];
                share.shareItemsError = true;
                share.shareItemsErrorMessage = shareManagementHelpers.formatShareItemsErrorMessage(error);
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
        const normalizeTransportPriority = (values, availableValues) => {
            const allowed = new Set(availableValues || []);
            return (values || [])
                .map(item => String(item || '').trim().toUpperCase())
                .filter(item => allowed.has(item))
                .filter((item, index, arr) => arr.indexOf(item) === index);
        };

        const ensureTransportPriorityDefaults = () => {
            const uploadDefaults = uploadTransportOptions.map(item => item.value);
            const downloadDefaults = downloadTransportOptions.map(item => item.value);
            const normalizedUpload = normalizeTransportPriority(fileConfig.value.uploadTransportPriority, uploadDefaults);
            const normalizedDownload = normalizeTransportPriority(fileConfig.value.downloadTransportPriority, downloadDefaults);
            fileConfig.value.uploadTransportPriority = normalizedUpload.length ? normalizedUpload : uploadDefaults.slice();
            fileConfig.value.downloadTransportPriority = normalizedDownload.length ? normalizedDownload : downloadDefaults.slice();
            fileConfig.value.uploadTransportMode = fileConfig.value.uploadTransportPriority[0] || 'APPLICATION';
            fileConfig.value.downloadTransportMode = fileConfig.value.downloadTransportPriority[0] || 'APPLICATION';
        };

        const toggleTransportPriority = (target, mode, enabled) => {
            const key = target === 'download' ? 'downloadTransportPriority' : 'uploadTransportPriority';
            const options = target === 'download' ? downloadTransportOptions : uploadTransportOptions;
            const defaultOrder = options.map(item => item.value);
            const current = normalizeTransportPriority(fileConfig.value[key], defaultOrder);
            let next;
            if (enabled) {
                next = current.includes(mode) ? current.slice() : current.concat(mode);
                next.sort((a, b) => defaultOrder.indexOf(a) - defaultOrder.indexOf(b));
            } else {
                next = current.filter(item => item !== mode);
            }
            fileConfig.value[key] = next;
            if (next.length > 0) {
                if (target === 'download') {
                    fileConfig.value.downloadTransportMode = next[0];
                } else {
                    fileConfig.value.uploadTransportMode = next[0];
                }
            }
        };

        const moveTransportPriority = (target, index, direction) => {
            const key = target === 'download' ? 'downloadTransportPriority' : 'uploadTransportPriority';
            const current = (fileConfig.value[key] || []).slice();
            const nextIndex = index + direction;
            if (index < 0 || nextIndex < 0 || nextIndex >= current.length) {
                return;
            }
            const swapped = current[index];
            current[index] = current[nextIndex];
            current[nextIndex] = swapped;
            fileConfig.value[key] = current;
            if (current.length > 0) {
                if (target === 'download') {
                    fileConfig.value.downloadTransportMode = current[0];
                } else {
                    fileConfig.value.uploadTransportMode = current[0];
                }
            }
        };

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
                    fileConfig.value.fileDropMode = res.data.fileDropMode || 'INSTANT';
                    fileConfig.value.uploadDialogMode = res.data.uploadDialogMode || 'INSTANT';
                    fileConfig.value.uploadTransportMode = res.data.uploadTransportMode || 'APPLICATION';
                    fileConfig.value.downloadTransportMode = res.data.downloadTransportMode || 'APPLICATION';
                    fileConfig.value.uploadTransportPriority = res.data.uploadTransportPriority || [fileConfig.value.uploadTransportMode];
                    fileConfig.value.downloadTransportPriority = res.data.downloadTransportPriority || [fileConfig.value.downloadTransportMode];
                    fileConfig.value.uploadProxyThresholdMB = res.data.uploadProxyThresholdMB || 256;
                    fileConfig.value.downloadProxyThresholdMB = res.data.downloadProxyThresholdMB || 128;
                    fileConfig.value.fileRowHiddenActions = res.data.fileRowHiddenActions || ['DELETE', 'MOVE', 'COPY'];
                    ensureTransportPriorityDefaults();
                }
            } catch (error) {
                const detail = error?.response?.data?.message || error?.message || '';
                ElementPlus.ElMessage.error(detail ? '加载配置失败: ' + detail : '加载配置失败');
            }
        };

        const saveFileConfig = async () => {
            try {
                ensureTransportPriorityDefaults();
                const extensions = fileConfig.value.allowedExtensionsText
                    .split(',')
                    .map(e => e.trim())
                    .filter(e => e);
                const res = await api.post('/admin/file-config', {
                    allowedExtensions: extensions,
                    maxFileSize: fileConfig.value.maxFileSizeMB * 1024 * 1024,
                    frontendDownloadThresholdMB: fileConfig.value.frontendDownloadThresholdMB,
                    fileDropMode: fileConfig.value.fileDropMode,
                    uploadDialogMode: fileConfig.value.uploadDialogMode,
                    uploadTransportMode: fileConfig.value.uploadTransportMode,
                    downloadTransportMode: fileConfig.value.downloadTransportMode,
                    uploadTransportPriority: fileConfig.value.uploadTransportPriority,
                    downloadTransportPriority: fileConfig.value.downloadTransportPriority,
                    uploadProxyThresholdMB: fileConfig.value.uploadProxyThresholdMB,
                    downloadProxyThresholdMB: fileConfig.value.downloadProxyThresholdMB,
                    fileRowHiddenActions: fileConfig.value.fileRowHiddenActions
                });
                if (res.code === 200) {
                    ElementPlus.ElMessage.success('保存成功');
                }
            } catch (error) {
                const detail = error?.response?.data?.message || error?.message || '';
                ElementPlus.ElMessage.error(detail ? '保存配置失败: ' + detail : '保存配置失败');
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
                    runtimeConfig.value = {
                        httpPort: null,
                        httpsPort: null,
                        forceHttps: false,
                        appVersion: '',
                        fileRowHiddenActions: ['DELETE', 'MOVE', 'COPY'],
                        ...(res.data || {})
                    };
                }
            } catch (error) {
                // Ignore and fallback to current URL scheme replacement.
            }
        };

        const handleDocumentKeydown = (e) => {
            if (navigationOpen.value) {
                if (e.key === 'Escape') { e.preventDefault(); closeNavigation(); return; }
                if (e.key === 'Tab') {
                    const elements = Array.from(document.querySelectorAll('#workspace-navigation button, #workspace-navigation [tabindex]:not([tabindex="-1"])')).filter(node => node.offsetParent !== null);
                    const first = elements[0], last = elements[elements.length - 1];
                    if (first && e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
                    else if (last && !e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
                }
            }
            const isPathShortcut = (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'l';
            if (!isPathShortcut || currentMenu.value !== 'files') {
                return;
            }
            e.preventDefault();
            startPathEdit();
        };

        const handleWindowError = (event) => {
            recordClientDiagnostic('error', 'window-error', {
                message: event.message,
                source: event.filename,
                line: event.lineno,
                column: event.colno
            });
        };

        const handleUnhandledRejection = (event) => {
            const reason = event.reason || {};
            recordClientDiagnostic('error', 'unhandled-rejection', {
                message: reason.message || String(reason),
                stack: reason.stack || ''
            });
        };

        // Lifecycle
        const clientCertEnabled = computed(() => window.location.protocol === 'https:' && runtimeConfig.value.clientCertificateEnabled === true);
        onMounted(() => {
            loadRuntimeConfig();
            checkAuth();
            document.addEventListener('click', handleDocumentClick);
            document.addEventListener('keydown', handleDocumentKeydown);
            window.addEventListener('resize', handleNavigationResize);
            window.addEventListener('error', handleWindowError);
            window.addEventListener('unhandledrejection', handleUnhandledRejection);
        });

        onUnmounted(() => {
            document.removeEventListener('click', handleDocumentClick);
            document.removeEventListener('keydown', handleDocumentKeydown);
            window.removeEventListener('resize', handleNavigationResize);
            window.removeEventListener('error', handleWindowError);
            window.removeEventListener('unhandledrejection', handleUnhandledRejection);
            stopSyncingRetry();
            disconnectMessageSocket();
            clearBucketStatsPollTimer();
        });

        return {
            isLoggedIn,
            currentTheme,
            toggleTheme,
            navigationOpen,
            toggleNavigation,
            closeNavigation,
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
            bucketStatsRefreshing,
            bucketStatsRefreshStatus,
            fileList,
            fileLoading,
            fileDropActive,
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
            showTransferDialog,
            showRenameDialog,
            uploadRef,
            uploadTasks,
            showFolderDialog,
            showShareDialog,
            showBatchShareDialog,
            showUserDialog,
            showProfileDialog,
            showRoleDialog,
            showMinioKeyDialog,
            showHelpDialog,
            showVersionDialog,
            showDiagnosticsDialog,
            showPreviewDialog,
            clientDiagnosticsLogs,
            downloadTasks,
            downloadCenterVisible,
            downloadCenterMinimized,
            messageCenterVisible,
            messageCenterTab,
            showForwardDialog,
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
            copyPreviewText,
            closePreviewDialog,
            transferState,
            renameState,
            transferSummary,
            transferTargetLabel,
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
            messageCenterMessages,
            recipientList,
            recipientLoading,
            forwardMessageTarget,
            forwardRecipientUserIds,
            feedbackForm,
            feedbackCategories,
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
            fileRowActionOptions,
            pendingUploadFiles,
            uploadBatchRenameRule,
            uploadDialogModeOptions,
            uploadTransportOptions,
            downloadTransportOptions,
            uploadTransportLabelMap,
            downloadTransportLabelMap,
            shareConfig,
            permissionInfo,
            permissionList,
            isAdmin,
            certificateApi: api,
            certificateIssuanceEnabled: computed(() => runtimeConfig.value.certificateIssuanceEnabled === true),
            copyText,
            isSuperAdmin,
            menuPermissionOptions,
            apiPermissionOptions,
            availableBucketOptions,
            bucketActionOptions,
            currentMenuAccessible,
            shouldShowRoleBucketPermissionEditor,
            canOpenDiagnostics,
            currentViewTitle,
            currentViewDescription,
            workbenchOverview,
            workbenchLoading,
            workbenchPreferenceSaving,
            isCurrentDirectoryFavorite,
            bucketCount,
            folderCount,
            currentFileCount,
            currentPageSize,
            activeDownloadTaskCount,
            failedDownloadTaskCount,
            primaryDownloadTask,
            transferCenterVisible,
            transferCenterMinimized,
            transferCenterTab,
            transferUploadTasks,
            unifiedTransferTasks,
            activeTransferCount,
            filteredTransferTasks,
            completedTransferCount,
            transferTabCounts,
            clearCompletedTransfers,
            handleTransferTaskAction,
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
            handleCertLogin, clientCertEnabled, showCertDialog, userCertificates,
            logout,
            handleCommand,
            bindPassword,
            handleBindCert,
            handleRevokeCert,
            saveProfile,
            handleMenuSelect,
            loadBuckets,
            refreshBucketStats,
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
            openWorkbenchDirectory,
            openWorkbenchShareReminder,
            removeFavoriteDirectory,
            toggleCurrentDirectoryFavorite,
            updateWorkbenchPreference,
            openRenameDialog,
            submitRename,
            handleFileNameClick,
            canSelectFileRow,
            handleFileDragEnter,
            handleFileDragOver,
            handleFileDragLeave,
            handleFileDrop,
            uploadDialogDropActive,
            handleUploadDialogDragOver,
            handleUploadDialogDragLeave,
            handleUploadDialogDrop,
            downloadFile,
            previewFile,
            deleteFile,
            getFileRowActions,
            batchShare,
            createBatchShare,
            batchDownload,
            batchDownloadFromDialog,
            batchDelete,
            batchMove,
            batchCopy,
            createFolder,
            shareFile,
            handleShareTargetTypeChange,
            handleBatchShareTargetTypeChange,
            createShare,
            handleSelectionChange,
            submitUpload,
            applyUploadBatchRenameRule,
            handleUploadDialogChange,
            removePendingUploadFile,
            cancelUpload,
            openTransferDialog,
            loadTransferFolderOptions,
            enterTransferFolder,
            backTransferFolder,
            confirmTransfer,
            uploading,
            copyShareLink,
            deleteShare,
            cancelShare,
            sharePage,
            sharePageSize,
            shareTotal,
            shareValidOnly,
            handleShareValidOnlyChange,
            handleShareSizeChange,
            handleSharePageChange,
            handleShareExpand,
            loadInbox,
            inboxPage,
            inboxPageSize,
            inboxTotal,
            inboxValidOnly,
            handleInboxValidOnlyChange,
            handleInboxSizeChange,
            handleInboxPageChange,
            openInboxShare,
            markInboxRead,
            deleteInboxMessage,
            openForwardDialogForMessage,
            forwardInboxMessage,
            submitFeedback,
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
            toggleTransportPriority,
            moveTransportPriority,
            clearClientDiagnostics,
            copyClientDiagnostics,
            loadShareConfig,
            saveShareConfig,
            openDownloadCenter,
            minimizeDownloadCenter,
            restoreDownloadCenter,
            pauseDownloadTask,
            resumeDownloadTask,
            cancelDownloadTask,
            retryDownloadTask,
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
app.component('standard-cert-admin', StandardCertUi.component);
app.mount('#app');
