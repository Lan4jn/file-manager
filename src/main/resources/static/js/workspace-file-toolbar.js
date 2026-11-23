(function (window) {
    'use strict';
    const bindings = new WeakMap();

    function unmount(toolbar) {
        bindings.get(toolbar)?.abort();
        bindings.delete(toolbar);
    }

    function mount(toolbar) {
        unmount(toolbar);
        const details = toolbar.querySelector('.file-toolbar-more');
        const panel = toolbar.querySelector('#file-toolbar-overflow');
        const view = toolbar.ownerDocument.defaultView;
        const binding = new AbortController();
        const capture = { capture: true, signal: binding.signal };
        bindings.set(toolbar, binding);

        const close = (restoreFocus) => {
            details.open = false;
            if (restoreFocus) details.firstElementChild.focus();
        };
        view.addEventListener('keydown', (event) => {
            if (event.key !== 'Escape' || !details.open || !details.getClientRects().length) return;
            if (event.target.matches?.('.path-edit-input-native')) {
                close(false);
                return;
            }
            event.preventDefault();
            event.stopPropagation();
            close(true);
        }, capture);
        view.addEventListener('pointerdown', (event) => {
            if (details.open && !toolbar.contains(event.target)) close(false);
        }, capture);
        view.addEventListener('focusin', (event) => {
            if (details.open && !toolbar.contains(event.target)) close(false);
        }, capture);
        panel.addEventListener('click', (event) => {
            if (details.open && details.getClientRects().length && event.target.closest('button:not(:disabled)')) close(true);
        }, { signal: binding.signal });
    }

    window.WorkspaceFileToolbar = { mount, unmount };
})(window);
