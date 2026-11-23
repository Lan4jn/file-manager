// Presentation-only adapter; business events, network clients and Vue state stay untouched.
import { refresh } from '../vendor/taste/motion/motion.js';
let scheduled = false;
const apply = () => {
    scheduled = false;
    document.querySelectorAll('.workspace-app .el-button, .workspace-share button').forEach(button => {
        const enabled = !button.disabled && !button.classList.contains('is-disabled');
        if (button.classList.contains('btn') !== enabled) button.classList.toggle('btn', enabled);
    });
    document.querySelectorAll('.workspace-share .share-brand a').forEach(link => {
        if (!link.classList.contains('link')) link.classList.add('link');
    });
    refresh();
};
const schedule = () => {
    if (!scheduled) { scheduled = true; requestAnimationFrame(apply); }
};
new MutationObserver(schedule).observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'disabled']});
schedule();
