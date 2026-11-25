const test = require('node:test');
const assert = require('node:assert/strict');
const {
    normalizeTheme,
    resolveInitialTheme,
    persistTheme,
    applyTheme,
    initTheme,
    toggleTheme,
    getSafeStorage,
} = require('../../main/resources/static/js/theme-helpers.js');

function mockDocument() {
    const toggleCalls = [];
    const metaAttributes = {};
    const doc = {
        documentElement: {
            dataset: {},
            style: {},
            classList: {
                toggle(name, force) {
                    toggleCalls.push([name, force]);
                },
            },
        },
        querySelector(selector) {
            if (selector !== 'meta[name="theme-color"]') return null;
            return {
                setAttribute(name, value) {
                    metaAttributes[name] = value;
                },
            };
        },
    };
    return { doc, toggleCalls, metaAttributes };
}

test('normalizeTheme defaults to light and accepts only exact dark', () => {
    assert.equal(normalizeTheme(), 'light');
    for (const value of [null, '', 'light', 'Dark', ' dark ', 'system', true]) {
        assert.equal(normalizeTheme(value), 'light');
    }
    assert.equal(normalizeTheme('dark'), 'dark');
});

test('resolveInitialTheme reads storage and falls back to light on errors', () => {
    const storage = {
        getItem(key) {
            assert.equal(key, 'fm-theme');
            return 'dark';
        },
    };
    const blockedStorage = {
        getItem() {
            throw new Error('Storage is unavailable');
        },
    };

    assert.equal(resolveInitialTheme(storage), 'dark');
    assert.equal(resolveInitialTheme({ getItem: () => null }), 'light');
    assert.equal(resolveInitialTheme(blockedStorage), 'light');
    assert.equal(resolveInitialTheme(), 'light');
});

test('persistTheme stores normalized values and tolerates write errors', () => {
    const writes = [];
    const storage = { setItem: (key, value) => writes.push([key, value]) };
    const blockedStorage = {
        setItem() {
            throw new Error('Storage quota exceeded');
        },
    };

    assert.equal(persistTheme('dark', storage), 'dark');
    assert.equal(persistTheme('system', storage), 'light');
    assert.deepEqual(writes, [['fm-theme', 'dark'], ['fm-theme', 'light']]);
    assert.equal(persistTheme('dark', blockedStorage), 'dark');
});

test('applyTheme synchronizes the theme, dark class, color scheme and theme color', () => {
    const { doc, toggleCalls, metaAttributes } = mockDocument();
    const root = doc.documentElement;

    assert.equal(applyTheme('dark', doc), 'dark');
    assert.equal(root.dataset.theme, 'dark');
    assert.equal(root.style.colorScheme, 'dark');
    assert.equal(metaAttributes.content, '#13161b');

    assert.equal(applyTheme('system', doc), 'light');
    assert.equal(root.dataset.theme, 'light');
    assert.equal(root.style.colorScheme, 'light');
    assert.equal(metaAttributes.content, '#fafafa');
    assert.deepEqual(toggleCalls, [['dark', true], ['dark', false]]);
});

test('initTheme restores the stored theme and toggleTheme switches both ways', () => {
    const { doc } = mockDocument();
    const saved = new Map([['fm-theme', 'dark']]);
    const storage = {
        getItem: (key) => saved.get(key),
        setItem: (key, value) => saved.set(key, value),
    };

    const initial = initTheme({ localStorage: storage }, doc);
    assert.equal(initial, 'dark');
    assert.equal(doc.documentElement.dataset.theme, 'dark');

    const next = toggleTheme(initial, storage, doc);
    assert.equal(next, 'light');
    assert.equal(doc.documentElement.dataset.theme, 'light');
    assert.equal(saved.get('fm-theme'), 'light');

    assert.equal(toggleTheme(next, storage, doc), 'dark');
    assert.equal(doc.documentElement.dataset.theme, 'dark');
    assert.equal(saved.get('fm-theme'), 'dark');
});

test('getSafeStorage safely returns storage or null when property access throws', () => {
    assert.equal(typeof getSafeStorage, 'function');
    const normalStorage = {};
    assert.equal(getSafeStorage({ localStorage: normalStorage }), normalStorage);
    assert.equal(getSafeStorage(null), null);
    assert.equal(getSafeStorage({}), null);

    const throwingWin = {
        get localStorage() {
            throw new Error('SecurityError: The operation is insecure');
        },
    };
    assert.equal(getSafeStorage(throwingWin), null);
});

test('initTheme and toggleTheme tolerate storage access errors without throwing', () => {
    const { doc } = mockDocument();
    const throwingWin = {
        get localStorage() {
            throw new Error('SecurityError: Access denied');
        },
    };

    // Should not throw and should default to light
    const theme = initTheme(throwingWin, doc);
    assert.equal(theme, 'light');
    assert.equal(doc.documentElement.dataset.theme, 'light');

    // toggleTheme with throwing storage or null storage
    const throwingStorage = {
        getItem() { throw new Error('SecurityError'); },
        setItem() { throw new Error('QuotaExceededError'); },
    };
    const nextTheme = toggleTheme('light', throwingStorage, doc);
    assert.equal(nextTheme, 'dark');
    assert.equal(doc.documentElement.dataset.theme, 'dark');

    const backTheme = toggleTheme(nextTheme, null, doc);
    assert.equal(backTheme, 'light');
    assert.equal(doc.documentElement.dataset.theme, 'light');
});
