'use strict';

const STATUSES = ['SAVED', 'APPLIED', 'OA', 'PHONE_SCREEN', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'];
const TERMINAL = new Set(['REJECTED', 'WITHDRAWN']);
const PAGE_SIZE = 20;
const TOKEN_KEY = 'jobtrail.token';

// OA is an initialism, so title-casing it the way the others are would read "Oa".
const label = (status) => status === 'OA'
    ? 'OA'
    : status.replace('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
const $ = (sel) => document.querySelector(sel);

const state = {
    token: localStorage.getItem(TOKEN_KEY),
    page: 0,
    status: '',
    sort: 'createdAt,desc',
    lastPage: null,
};

/* ─────────────────────────  API  ───────────────────────── */

class ApiError extends Error {
    constructor(status, problem) {
        super(problem?.detail || `Request failed (${status})`);
        this.status = status;
        this.fieldErrors = problem?.errors || null;
    }
}

async function api(path, { method = 'GET', body } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;

    const res = await fetch(`/api/v1${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (res.status === 401 && state.token) {
        signOut();
        throw new ApiError(401, { detail: 'Your session expired. Please sign in again.' });
    }
    if (res.status === 204) return null;

    const payload = await res.json().catch(() => null);
    if (!res.ok) throw new ApiError(res.status, payload);
    return payload;
}

/* ─────────────────────────  Utilities  ───────────────────────── */

function formatDate(iso) {
    const d = new Date(iso);
    const sameYear = d.getFullYear() === new Date().getFullYear();
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', ...(sameYear ? {} : { year: 'numeric' }) });
}

function formatDateTime(iso) {
    return new Date(iso).toLocaleString(undefined, {
        month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
    });
}

function relative(iso) {
    const days = Math.floor((Date.now() - new Date(iso)) / 86400000);
    if (days === 0) return 'today';
    if (days === 1) return 'yesterday';
    if (days < 30) return `${days} days ago`;
    return formatDate(iso);
}

const pill = (status) => `<span class="pill" data-s="${status}">${label(status)}</span>`;

const initials = (name) => name.trim().slice(0, 2).toUpperCase();

let toastTimer;
function toast(message, kind = 'ok') {
    const el = $('#toast');
    el.textContent = message;
    el.dataset.kind = kind;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 3000);
}

/* ─────────────────────────  Auth  ───────────────────────── */

function emailFromToken(token) {
    try {
        const claims = JSON.parse(atob(token.split('.')[1]));
        return claims.email || null;
    } catch {
        return null;
    }
}

function signOut() {
    state.token = null;
    localStorage.removeItem(TOKEN_KEY);
    $('#app-screen').hidden = true;
    $('#auth-screen').hidden = false;
    closeDrawer();
    closeModal();
}

async function signIn(mode, email, password) {
    const { token } = await api(`/auth/${mode}`, { method: 'POST', body: { email, password } });
    state.token = token;
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem('jobtrail.email', email);
    await enterApp();
}

async function enterApp() {
    $('#auth-screen').hidden = true;
    $('#app-screen').hidden = false;
    $('#user-email').textContent = localStorage.getItem('jobtrail.email') || emailFromToken(state.token) || '';
    state.page = 0;
    await refresh();
}

/* ─────────────────────────  Rendering  ───────────────────────── */

function renderStats(summary) {
    const cards = [
        `<div class="stat is-feature">
            <div class="stat-label">Active</div>
            <div class="stat-value">${summary.active}</div>
         </div>`,
        `<div class="stat">
            <div class="stat-label">Total</div>
            <div class="stat-value">${summary.total}</div>
         </div>`,
    ];

    // Surface only the stages that actually have applications in them.
    for (const status of STATUSES) {
        const count = summary.byStatus[status] || 0;
        if (count === 0) continue;
        cards.push(
            `<div class="stat">
                <div class="stat-label">${label(status)}</div>
                <div class="stat-value">${count}</div>
             </div>`
        );
    }
    $('#stats').innerHTML = cards.join('');
}

function renderList(page) {
    const list = $('#list');

    if (page.totalElements === 0) {
        const filtered = state.status !== '';
        list.innerHTML = `
            <div class="empty">
                <h3>${filtered ? 'Nothing at this stage' : 'No applications yet'}</h3>
                <p>${filtered
                    ? 'Try a different status filter.'
                    : 'Add the first role you have applied to and JobTrail will track every step from here.'}</p>
                ${filtered ? '' : '<button class="btn btn-primary" id="empty-new">Add an application</button>'}
            </div>`;
        $('#empty-new')?.addEventListener('click', () => openModal());
        $('#pagination').hidden = true;
        return;
    }

    list.innerHTML = page.content.map((app) => `
        <article class="card" tabindex="0" role="button" data-id="${app.id}">
            <div class="card-logo">${initials(app.companyName)}</div>
            <div class="card-main">
                <div class="card-role">${escapeHtml(app.position)}</div>
                <div class="card-company">${escapeHtml(app.companyName)}</div>
            </div>
            <div class="card-meta">
                <span class="card-date">${relative(app.updatedAt)}</span>
                ${pill(app.status)}
            </div>
        </article>`).join('');

    $('#pagination').hidden = page.totalPages <= 1;
    $('#page-info').textContent = `Page ${page.number + 1} of ${page.totalPages}`;
    $('#prev-page').disabled = page.first;
    $('#next-page').disabled = page.last;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

async function refresh() {
    $('#list').innerHTML = '<div class="skeleton"></div>'.repeat(3);

    const params = new URLSearchParams({ page: state.page, size: PAGE_SIZE, sort: state.sort });
    if (state.status) params.set('status', state.status);

    try {
        const [page, summary] = await Promise.all([
            api(`/applications?${params}`),
            api('/analytics/summary'),
        ]);
        state.lastPage = page;
        renderStats(summary);
        renderList(page);
        $('#result-count').textContent =
            page.totalElements === 1 ? '1 application' : `${page.totalElements} applications`;
    } catch (err) {
        if (err.status !== 401) toast(err.message, 'error');
    }
}

/* ─────────────────────────  Detail drawer  ───────────────────────── */

async function openDrawer(id) {
    $('#drawer-backdrop').hidden = false;
    $('#drawer').hidden = false;
    $('#drawer-body').innerHTML = '<div class="drawer-section"><div class="skeleton"></div></div>';

    let app;
    try {
        app = await api(`/applications/${id}`);
    } catch (err) {
        closeDrawer();
        toast(err.message, 'error');
        return;
    }

    const history = [...app.statusEvents].reverse();

    $('#drawer-body').innerHTML = `
        <div class="drawer-head">
            <div class="drawer-top">
                <div>
                    <h3>${escapeHtml(app.position)}</h3>
                    <div class="drawer-company">${escapeHtml(app.companyName)}</div>
                </div>
                <button class="icon-btn" id="drawer-close" aria-label="Close">
                    <svg viewBox="0 0 24 24" class="icon"><path d="M18 6L6 18M6 6l12 12"/></svg>
                </button>
            </div>
            <div style="margin-top:14px">${pill(app.status)}</div>
        </div>

        <div class="drawer-actions">
            <button class="btn btn-ghost btn-sm" id="drawer-edit">Edit</button>
            <button class="btn btn-danger btn-sm" id="drawer-delete">Delete</button>
        </div>

        <div class="drawer-section">
            <h4>Details</h4>
            <dl style="margin:0">
                <div class="meta-row"><dt>Added</dt><dd>${formatDate(app.createdAt)}</dd></div>
                <div class="meta-row"><dt>Last update</dt><dd>${formatDate(app.updatedAt)}</dd></div>
                ${app.jobUrl ? `<div class="meta-row"><dt>Posting</dt>
                    <dd><a href="${escapeHtml(app.jobUrl)}" target="_blank" rel="noopener noreferrer">Open link</a></dd></div>` : ''}
            </dl>
            ${app.notes ? `<h4 style="margin-top:20px">Notes</h4><p class="notes">${escapeHtml(app.notes)}</p>` : ''}
        </div>

        <div class="drawer-section" style="border-top:1px solid var(--border)">
            <h4>History &middot; ${history.length} ${history.length === 1 ? 'step' : 'steps'}</h4>
            <ul class="timeline">
                ${history.map((e) => `
                    <li>
                        ${pill(e.status)}
                        <div class="timeline-when">${formatDateTime(e.occurredAt)}</div>
                    </li>`).join('')}
            </ul>
        </div>`;

    $('#drawer-close').addEventListener('click', closeDrawer);
    $('#drawer-edit').addEventListener('click', () => { closeDrawer(); openModal(app); });
    $('#drawer-delete').addEventListener('click', () => remove(app));
}

function closeDrawer() {
    $('#drawer').hidden = true;
    $('#drawer-backdrop').hidden = true;
}

async function remove(app) {
    if (!confirm(`Delete the ${app.position} application at ${app.companyName}?\n\nIts status history goes with it.`)) return;
    try {
        await api(`/applications/${app.id}`, { method: 'DELETE' });
        closeDrawer();
        toast('Application deleted');
        await refresh();
    } catch (err) {
        toast(err.message, 'error');
    }
}

/* ─────────────────────────  Editor  ───────────────────────── */

let editingId = null;

function openModal(app = null) {
    editingId = app?.id ?? null;
    const form = $('#app-form');
    form.reset();
    $('#form-error').hidden = true;
    $('#modal-title').textContent = app ? 'Edit application' : 'New application';

    if (app) {
        form.companyName.value = app.companyName;
        form.position.value = app.position;
        form.jobUrl.value = app.jobUrl || '';
        form.notes.value = app.notes || '';
        form.status.value = app.status;
    } else {
        form.status.value = 'SAVED';
    }

    $('#modal-backdrop').hidden = false;
    $('#modal').hidden = false;
    form.companyName.focus();
}

function closeModal() {
    $('#modal').hidden = true;
    $('#modal-backdrop').hidden = true;
    editingId = null;
}

async function save(event) {
    event.preventDefault();
    const form = event.target;
    const errorBox = $('#form-error');
    errorBox.hidden = true;

    const body = {
        companyName: form.companyName.value.trim(),
        position: form.position.value.trim(),
        jobUrl: form.jobUrl.value.trim() || null,
        notes: form.notes.value.trim() || null,
        status: form.status.value,
    };

    if (!body.companyName || !body.position) {
        errorBox.textContent = 'Company and role are both required.';
        errorBox.hidden = false;
        return;
    }

    const submit = form.querySelector('button[type=submit]');
    submit.disabled = true;

    try {
        if (editingId) {
            await api(`/applications/${editingId}`, { method: 'PUT', body });
            toast('Application updated');
        } else {
            await api('/applications', { method: 'POST', body });
            toast('Application added');
        }
        closeModal();
        await refresh();
    } catch (err) {
        errorBox.textContent = err.fieldErrors
            ? Object.entries(err.fieldErrors).map(([field, msg]) => `${field}: ${msg}`).join(' · ')
            : err.message;
        errorBox.hidden = false;
    } finally {
        submit.disabled = false;
    }
}

/* ─────────────────────────  Wiring  ───────────────────────── */

function populateSelects() {
    const options = STATUSES.map((s) => `<option value="${s}">${label(s)}</option>`).join('');
    $('#form-status').innerHTML = options;
    $('#filter-status').insertAdjacentHTML('beforeend', options);
}

function bindAuth() {
    let mode = 'login';

    document.querySelectorAll('.tab').forEach((tab) => {
        tab.addEventListener('click', () => {
            mode = tab.dataset.mode;
            document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('is-active', t === tab));
            $('#auth-form').querySelector('.btn-label').textContent = mode === 'login' ? 'Sign in' : 'Create account';
            $('#auth-error').hidden = true;
        });
    });

    $('#fill-demo').addEventListener('click', () => {
        const form = $('#auth-form');
        form.email.value = 'demo@jobtrail.dev';
        form.password.value = 'password123';
        form.password.focus();
    });

    $('#auth-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const form = event.target;
        const errorBox = $('#auth-error');
        errorBox.hidden = true;

        const submit = form.querySelector('button[type=submit]');
        submit.disabled = true;
        try {
            await signIn(mode, form.email.value.trim(), form.password.value);
        } catch (err) {
            errorBox.textContent = err.status === 401
                ? 'That email and password do not match an account.'
                : err.fieldErrors
                    ? Object.values(err.fieldErrors).join(' · ')
                    : err.message;
            errorBox.hidden = false;
        } finally {
            submit.disabled = false;
        }
    });
}

function bindApp() {
    $('#logout').addEventListener('click', signOut);
    $('#new-app').addEventListener('click', () => openModal());

    $('#filter-status').addEventListener('change', (e) => {
        state.status = e.target.value;
        state.page = 0;
        refresh();
    });

    $('#sort-by').addEventListener('change', (e) => {
        state.sort = e.target.value;
        state.page = 0;
        refresh();
    });

    $('#prev-page').addEventListener('click', () => { state.page--; refresh(); });
    $('#next-page').addEventListener('click', () => { state.page++; refresh(); });

    $('#list').addEventListener('click', (e) => {
        const card = e.target.closest('.card');
        if (card) openDrawer(card.dataset.id);
    });
    $('#list').addEventListener('keydown', (e) => {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        const card = e.target.closest('.card');
        if (card) { e.preventDefault(); openDrawer(card.dataset.id); }
    });

    $('#app-form').addEventListener('submit', save);
    document.querySelectorAll('[data-close-modal]').forEach((el) => el.addEventListener('click', closeModal));
    $('#modal-backdrop').addEventListener('click', closeModal);
    $('#drawer-backdrop').addEventListener('click', closeDrawer);

    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        if (!$('#modal').hidden) closeModal();
        else if (!$('#drawer').hidden) closeDrawer();
    });
}

populateSelects();
bindAuth();
bindApp();

if (state.token) {
    enterApp();
} else {
    $('#auth-screen').hidden = false;
}
