const linksInput = document.getElementById("linksInput");
const resolveButton = document.getElementById("resolveButton");
const submitButton = document.getElementById("submitButton");
const formMessage = document.getElementById("formMessage");
const progressWidget = document.getElementById("progressWidget");
const progressStageText = document.getElementById("progressStageText");
const progressPercentText = document.getElementById("progressPercentText");
const progressBarFill = document.getElementById("progressBarFill");
const progressMetaText = document.getElementById("progressMetaText");
const resolveTabButton = document.getElementById("resolveTabButton");
const taskTabButton = document.getElementById("taskTabButton");
const resolvePanel = document.getElementById("resolvePanel");
const resolveSummary = document.getElementById("resolveSummary");
const resolveList = document.getElementById("resolveList");
const taskPanel = document.getElementById("taskPanel");
const taskEmptyState = document.getElementById("taskEmptyState");
const taskContent = document.getElementById("taskContent");
const taskIdText = document.getElementById("taskIdText");
const taskStatusBadge = document.getElementById("taskStatusBadge");
const taskSummary = document.getElementById("taskSummary");
const taskMetrics = document.getElementById("taskMetrics");
const metricTotal = document.getElementById("metricTotal");
const metricSuccess = document.getElementById("metricSuccess");
const metricFailed = document.getElementById("metricFailed");
const metricRunning = document.getElementById("metricRunning");
const downloadLink = document.getElementById("downloadLink");
const resultList = document.getElementById("resultList");
const resultPagination = document.getElementById("resultPagination");

const appBasePath = window.bilibiliAudioConfig?.basePath || "";
const pollInterval = window.bilibiliAudioConfig?.pollIntervalMs || 2000;
const pageSize = 10;
let pollTimer = null;
let resolvedGroups = [];
let resolvePages = [];
let taskResults = [];
let resultPage = 1;
let activeTab = "resolve";
let hasTask = false;
let resolveGroupCollapsed = {};
let taskItemCollapsed = {};

resolveTabButton.addEventListener("click", () => switchTab("resolve"));
taskTabButton.addEventListener("click", () => switchTab("task"));

resolveButton.addEventListener("click", async () => {
    const links = getRawLinks();
    if (!links.length) {
        formMessage.textContent = "请至少输入一个 Bilibili 视频链接。";
        return;
    }

    resolveButton.disabled = true;
    submitButton.disabled = true;
    formMessage.textContent = "正在解析链接和分集...";
    resolveList.innerHTML = "";
    resolvedGroups = [];
    resolvePages = [];
    resolveGroupCollapsed = {};
    updateProgress("RESOLVING_LINKS", 35, "正在解析链接", "服务正在向后端请求分集信息，请稍候。");

    try {
        const response = await fetch(buildAppUrl("/api/tasks/resolve-links"), {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ links })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "解析失败");
        }
        updateProgress("RESOLVING_LINKS", 80, "正在整理分集结果", "链接解析成功，正在渲染分集列表。");
        resolvedGroups = (payload.groups || []).map(normalizeGroup);
        resolvePages = resolvedGroups.map(() => 1);
        renderResolvedGroups();
        updateSelectedState();
        switchTab("resolve");
        updateProgress("RESOLVED", 100, "分集解析完成", `共解析 ${resolvedGroups.length} 组链接，已可开始选择分集。`);
        formMessage.textContent = getSelectedLinks().length > 0
            ? "链接已解析，确认勾选后即可开始处理。"
            : "解析完成，请先勾选要下载的分集。";
    } catch (error) {
        updateProgress("RESOLVE_FAILED", 100, "分集解析失败", error.message || "解析失败，请稍后重试。");
        formMessage.textContent = error.message || "解析失败，请稍后重试。";
    } finally {
        resolveButton.disabled = false;
    }
});

submitButton.addEventListener("click", async () => {
    const links = getSelectedLinks();
    if (!links.length) {
        formMessage.textContent = "请先解析链接并至少勾选一个分集。";
        return;
    }

    resolveButton.disabled = true;
    submitButton.disabled = true;
    formMessage.textContent = "正在创建任务...";
    updateProgress("CREATING_TASK", 8, "正在创建任务", `已选 ${links.length} 个分集，正在提交到后台。`);

    try {
        const response = await fetch(buildAppUrl("/api/tasks"), {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ links })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "提交失败");
        }
        formMessage.textContent = "任务已创建，正在后台处理。";
        showTaskPanel(payload.taskId, payload.status);
        switchTab("task");
        startPolling(payload.taskId);
    } catch (error) {
        updateProgress("TASK_FAILED", 100, "任务创建失败", error.message || "提交失败，请稍后重试。");
        formMessage.textContent = error.message || "提交失败，请稍后重试。";
        resolveButton.disabled = false;
        submitButton.disabled = getSelectedLinks().length === 0;
    }
});

resolveList.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
        return;
    }
    if (target.classList.contains("option-checkbox")) {
        const groupIndex = Number(target.dataset.groupIndex);
        const optionIndex = Number(target.dataset.optionIndex);
        resolvedGroups[groupIndex].options[optionIndex].selected = target.checked;
        renderResolvedGroups();
        updateSelectedState();
        return;
    }
    if (target.classList.contains("group-select-all")) {
        const groupIndex = Number(target.dataset.groupIndex);
        resolvedGroups[groupIndex].options.forEach((option) => {
            option.selected = target.checked;
        });
        renderResolvedGroups();
        updateSelectedState();
    }
});

resolveList.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
        return;
    }
    if (target.classList.contains("group-toggle-button")) {
        const groupIndex = Number(target.dataset.groupIndex);
        resolveGroupCollapsed[groupIndex] = !resolveGroupCollapsed[groupIndex];
        renderResolvedGroups();
        return;
    }
    if (target.classList.contains("resolve-page-button")) {
        const groupIndex = Number(target.dataset.groupIndex);
        const page = Number(target.dataset.page);
        if (!Number.isNaN(page) && page > 0) {
            resolvePages[groupIndex] = page;
            renderResolvedGroups();
        }
    }
});

resultList.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement) || !target.classList.contains("result-toggle-button")) {
        return;
    }
    const itemIndex = Number(target.dataset.itemIndex);
    taskItemCollapsed[itemIndex] = !taskItemCollapsed[itemIndex];
    renderTaskResults();
});

resultPagination.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement) || !target.classList.contains("result-page-button")) {
        return;
    }
    const page = Number(target.dataset.page);
    if (!Number.isNaN(page) && page > 0) {
        resultPage = page;
        renderTaskResults();
    }
});

function switchTab(tabName) {
    activeTab = tabName;
    const resolveActive = tabName === "resolve";
    resolveTabButton.classList.toggle("active", resolveActive);
    resolveTabButton.setAttribute("aria-selected", String(resolveActive));
    taskTabButton.classList.toggle("active", !resolveActive);
    taskTabButton.setAttribute("aria-selected", String(!resolveActive));
    resolvePanel.classList.toggle("hidden", !resolveActive);
    taskPanel.classList.toggle("hidden", resolveActive);
    renderTaskTabState();
}

function renderTaskTabState() {
    const showEmpty = !hasTask;
    taskEmptyState.classList.toggle("hidden", !showEmpty);
    taskContent.classList.toggle("hidden", showEmpty);
}

function normalizeGroup(group) {
    return {
        sourceLink: group.sourceLink,
        title: group.title,
        multipleParts: group.multipleParts,
        options: (group.options || []).map((option) => ({
            link: option.link,
            title: option.title,
            page: option.page,
            selected: Boolean(option.selected)
        }))
    };
}

function getRawLinks() {
    const rawText = linksInput.value;
    if (!rawText || !rawText.trim()) {
        return [];
    }
    return rawText.split(/\r?\n/).map((line) => line.trim());
}

function renderResolvedGroups() {
    resolveList.innerHTML = resolvedGroups.map((group, groupIndex) => renderGroup(group, groupIndex)).join("");
}

function renderGroup(group, groupIndex) {
    const header = escapeHtml(group.title || group.sourceLink);
    const sourceLink = escapeHtml(group.sourceLink);
    const tag = group.multipleParts
        ? '<span class="group-tag">多分集</span>'
        : '<span class="group-tag single">单视频</span>';
    const currentPage = resolvePages[groupIndex] || 1;
    const totalPages = Math.max(1, Math.ceil(group.options.length / pageSize));
    const safePage = Math.min(currentPage, totalPages);
    resolvePages[groupIndex] = safePage;
    const start = (safePage - 1) * pageSize;
    const end = start + pageSize;
    const pageOptions = group.options.slice(start, end);
    const allSelected = group.options.length > 0 && group.options.every((option) => option.selected);
    const options = pageOptions.map((option, pageIndex) => renderOption(option, groupIndex, start + pageIndex)).join("");
    const collapsed = Boolean(resolveGroupCollapsed[groupIndex]);
    const toggleText = collapsed ? "展开" : "收起";
    return `
        <article class="resolve-group">
            <div class="resolve-group-header">
                <div class="group-title-wrap">
                    <h3>${header}</h3>
                    <p class="resolve-source">${sourceLink}</p>
                </div>
                <div class="group-header-actions">
                    ${tag}
                    <button type="button" class="accordion-toggle group-toggle-button" data-group-index="${groupIndex}" aria-expanded="${!collapsed}">
                        ${toggleText}
                    </button>
                </div>
            </div>
            <div class="accordion-body ${collapsed ? "hidden" : ""}">
                <div class="group-toolbar">
                    <label class="select-all-toggle">
                        <input type="checkbox" class="group-select-all" data-group-index="${groupIndex}" ${allSelected ? "checked" : ""}>
                        <span>本合集全选</span>
                    </label>
                    ${renderPagination(safePage, totalPages, "resolve-page-button", groupIndex)}
                </div>
                <div class="option-list">${options}</div>
            </div>
        </article>
    `;
}

function renderOption(option, groupIndex, optionIndex) {
    const checked = option.selected ? "checked" : "";
    const title = escapeHtml(option.title || option.link);
    const link = escapeHtml(option.link);
    const inputId = `option-${groupIndex}-${optionIndex}`;
    return `
        <label class="option-item" for="${inputId}">
            <input id="${inputId}" type="checkbox" class="option-checkbox" data-group-index="${groupIndex}" data-option-index="${optionIndex}" ${checked}>
            <span class="option-content">
                <span class="option-title">${title}</span>
                <span class="option-link">${link}</span>
            </span>
        </label>
    `;
}

function renderPagination(currentPage, totalPages, buttonClass, groupIndex) {
    if (totalPages <= 1) {
        return `<div class="pagination compact"><span class="pagination-info">第 1 / 1 页</span></div>`;
    }
    const prevPage = Math.max(1, currentPage - 1);
    const nextPage = Math.min(totalPages, currentPage + 1);
    const groupAttr = typeof groupIndex === "number" ? `data-group-index="${groupIndex}"` : "";
    return `
        <div class="pagination compact">
            <button type="button" class="page-button ${buttonClass}" data-page="${prevPage}" ${groupAttr} ${currentPage === 1 ? "disabled" : ""}>上一页</button>
            <span class="pagination-info">第 ${currentPage} / ${totalPages} 页</span>
            <button type="button" class="page-button ${buttonClass}" data-page="${nextPage}" ${groupAttr} ${currentPage === totalPages ? "disabled" : ""}>下一页</button>
        </div>
    `;
}

function getSelectedLinks() {
    const deduped = [];
    const seen = new Set();
    resolvedGroups.forEach((group) => {
        group.options.forEach((option) => {
            if (option.selected && option.link && !seen.has(option.link)) {
                seen.add(option.link);
                deduped.push(option.link);
            }
        });
    });
    return deduped;
}

function updateSelectedState() {
    const selectedCount = getSelectedLinks().length;
    submitButton.disabled = selectedCount === 0;
    resolveSummary.textContent = selectedCount > 0
        ? `当前已选择 ${selectedCount} 个分集。每页默认展示 ${pageSize} 条。`
        : "请至少勾选一个分集后再开始处理。每页默认展示 10 条。";
}

function showTaskPanel(taskId, status) {
    hasTask = true;
    renderTaskTabState();
    taskIdText.textContent = taskId;
    updateStatusBadge(status);
    taskSummary.textContent = "任务已创建，正在获取处理状态。";
    taskMetrics.classList.remove("hidden");
    resetTaskMetrics();
    downloadLink.classList.add("hidden");
    taskResults = [];
    taskItemCollapsed = {};
    resultPage = 1;
    resultList.innerHTML = "";
    resultPagination.classList.add("hidden");
    resultPagination.innerHTML = "";
}

function startPolling(taskId) {
    stopPolling();
    pollTask(taskId);
    pollTimer = window.setInterval(() => pollTask(taskId), pollInterval);
}

function stopPolling() {
    if (pollTimer) {
        window.clearInterval(pollTimer);
        pollTimer = null;
    }
}

async function pollTask(taskId) {
    try {
        const response = await fetch(buildAppUrl(`/api/tasks/${taskId}`));
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "查询任务失败");
        }
        renderTask(payload);
        if (["SUCCESS", "PARTIAL_SUCCESS", "FAILED"].includes(payload.status)) {
            stopPolling();
            resolveButton.disabled = false;
            submitButton.disabled = getSelectedLinks().length === 0;
        }
    } catch (error) {
        taskSummary.textContent = error.message || "查询任务失败。";
        updateProgress("TASK_FAILED", 100, "任务处理失败", error.message || "查询任务失败。");
        stopPolling();
        resolveButton.disabled = false;
        submitButton.disabled = getSelectedLinks().length === 0;
    }
}

function renderTask(task) {
    updateStatusBadge(task.status);
    if (task.errorSummary) {
        taskSummary.textContent = task.errorSummary;
    } else if (task.status === "RUNNING") {
        taskSummary.textContent = "后台正在并行解析和转码，请稍候。";
    } else if (task.status === "SUCCESS") {
        taskSummary.textContent = "所有音频文件已准备完成。";
    } else if (task.status === "PARTIAL_SUCCESS") {
        taskSummary.textContent = "部分链接处理失败，但可下载成功生成的音频。";
    } else if (task.status === "FAILED") {
        taskSummary.textContent = "任务处理失败，没有可下载文件。";
    }

    if (task.downloadReady && task.downloadUrl) {
        downloadLink.href = buildAppUrl(task.downloadUrl);
        downloadLink.classList.remove("hidden");
    } else {
        downloadLink.classList.add("hidden");
    }

    taskResults = task.results || [];
    updateTaskMetrics(taskResults);
    updateTaskProgress(task);
    const totalPages = Math.max(1, Math.ceil(taskResults.length / pageSize));
    resultPage = Math.min(resultPage, totalPages);
    renderTaskResults();
}

function renderTaskResults() {
    const totalPages = Math.max(1, Math.ceil(taskResults.length / pageSize));
    const currentPage = Math.min(resultPage, totalPages);
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    resultList.innerHTML = taskResults.slice(start, end).map((item, index) => renderItem(item, start + index)).join("");
    if (taskResults.length > pageSize) {
        resultPagination.classList.remove("hidden");
        resultPagination.innerHTML = renderPagination(currentPage, totalPages, "result-page-button");
    } else {
        resultPagination.classList.add("hidden");
        resultPagination.innerHTML = "";
    }
}

function renderItem(item, itemIndex) {
    const title = escapeHtml(item.title || item.link);
    const collapsed = taskItemCollapsed[itemIndex] !== false;
    const toggleText = collapsed ? "展开" : "收起";
    const statusClass = item.status === "SUCCESS"
        ? "success"
        : item.status === "FAILED"
            ? "failed"
            : "";
    const meta = [];
    meta.push(`状态：${escapeHtml(item.status)}`);
    if (item.fileName) {
        meta.push(`文件：${escapeHtml(item.fileName)}`);
    }
    if (item.errorMessage) {
        meta.push(`错误：${escapeHtml(item.errorMessage)}`);
    }
    return `
        <li class="result-item">
            <div class="result-item-header">
                <div class="result-item-title-wrap">
                    <strong>${title}</strong>
                    <span class="result-item-status ${statusClass}">${escapeHtml(item.status)}</span>
                </div>
                <button type="button" class="accordion-toggle result-toggle-button" data-item-index="${itemIndex}" aria-expanded="${!collapsed}">
                    ${toggleText}
                </button>
            </div>
            <div class="accordion-body ${collapsed ? "hidden" : ""}">
                <p class="result-meta">${meta.join("<br>")}</p>
            </div>
        </li>
    `;
}

function updateTaskMetrics(results) {
    const total = results.length;
    let success = 0;
    let failed = 0;
    let running = 0;
    results.forEach((item) => {
        if (item.status === "SUCCESS") {
            success += 1;
        } else if (item.status === "FAILED") {
            failed += 1;
        } else {
            running += 1;
        }
    });
    metricTotal.textContent = String(total);
    metricSuccess.textContent = String(success);
    metricFailed.textContent = String(failed);
    metricRunning.textContent = String(running);
}

function updateTaskProgress(task) {
    const stageMap = {
        QUEUED: "任务已排队",
        VERIFYING_DEPENDENCIES: "正在检查依赖",
        PROCESSING_MEDIA: "正在处理音频",
        PACKAGING: "正在打包下载文件",
        COMPLETED: "任务处理完成",
        FAILED: "任务处理失败"
    };
    const stageText = stageMap[task.progressStage] || "任务处理中";
    const metaText = `已完成 ${task.completedCount || 0} / ${task.totalCount || 0}，成功 ${task.successCount || 0}，失败 ${task.failedCount || 0}。`;
    updateProgress(task.progressStage || "PROCESSING_MEDIA", task.progressPercent || 0, stageText, metaText);
}

function resetTaskMetrics() {
    metricTotal.textContent = "0";
    metricSuccess.textContent = "0";
    metricFailed.textContent = "0";
    metricRunning.textContent = "0";
}

function updateProgress(stageKey, percent, stageText, metaText) {
    progressWidget.classList.remove("hidden");
    const safePercent = Math.max(0, Math.min(100, Number(percent) || 0));
    progressStageText.textContent = stageText;
    progressPercentText.textContent = `${safePercent}%`;
    progressBarFill.style.width = `${safePercent}%`;
    progressMetaText.textContent = metaText;
    progressWidget.dataset.stage = stageKey;
}

function updateStatusBadge(status) {
    taskStatusBadge.textContent = status;
    taskStatusBadge.className = "badge";
    if (status === "SUCCESS" || status === "PARTIAL_SUCCESS") {
        taskStatusBadge.classList.add("success");
    } else if (status === "FAILED") {
        taskStatusBadge.classList.add("failed");
    }
}

function buildAppUrl(path) {
    if (!path) {
        return appBasePath || "/";
    }
    if (/^https?:\/\//.test(path)) {
        return path;
    }
    const normalizedPath = path.startsWith("/") ? path : `/${path}`;
    return `${appBasePath}${normalizedPath}`;
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

renderTaskTabState();
switchTab(activeTab);
