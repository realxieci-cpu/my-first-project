const state = {
  rootHandle: null,
  currentHandle: null,
  currentPath: [],
  entries: [],
  selectedEntry: null,
  supportsFSAccess: typeof window.showDirectoryPicker === 'function',
  fallbackTree: null,
};

const dom = {
  openDirectoryBtn: document.getElementById('open-directory-btn'),
  directoryPicker: document.getElementById('directory-picker'),
  newFolderBtn: document.getElementById('new-folder-btn'),
  newFileBtn: document.getElementById('new-file-btn'),
  currentPath: document.getElementById('current-path'),
  itemCount: document.getElementById('item-count'),
  folderCount: document.getElementById('folder-count'),
  fileCount: document.getElementById('file-count'),
  breadcrumbs: document.getElementById('breadcrumbs'),
  statusMessage: document.getElementById('status-message'),
  searchInput: document.getElementById('search-input'),
  sortSelect: document.getElementById('sort-select'),
  refreshBtn: document.getElementById('refresh-btn'),
  fileList: document.getElementById('file-list'),
  selectionCount: document.getElementById('selection-count'),
  previewKind: document.getElementById('preview-kind'),
  previewMeta: document.getElementById('preview-meta'),
  previewContent: document.getElementById('preview-content'),
  dropZone: document.getElementById('drop-zone'),
  actionTemplate: document.getElementById('action-template'),
};

const formatBytes = (bytes = 0) => {
  if (!bytes) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let idx = 0;
  while (value >= 1024 && idx < units.length - 1) {
    value /= 1024;
    idx += 1;
  }
  return `${value.toFixed(value >= 10 || idx === 0 ? 0 : 1)} ${units[idx]}`;
};

const formatDate = (value) => {
  if (!value) return '—';
  const date = typeof value === 'number' ? new Date(value) : value;
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
};

const setStatus = (message) => {
  dom.statusMessage.textContent = message;
};

const setActionAvailability = (enabled) => {
  dom.newFolderBtn.disabled = !enabled || !state.supportsFSAccess;
  dom.newFileBtn.disabled = !enabled || !state.supportsFSAccess;
  dom.refreshBtn.disabled = !enabled;
};

const updateStats = () => {
  const folderCount = state.entries.filter((entry) => entry.kind === 'directory').length;
  const fileCount = state.entries.filter((entry) => entry.kind === 'file').length;
  dom.currentPath.textContent = state.currentPath.length ? `/${state.currentPath.join('/')}` : '/';
  dom.itemCount.textContent = String(state.entries.length);
  dom.folderCount.textContent = String(folderCount);
  dom.fileCount.textContent = String(fileCount);
};

const buildFallbackTree = (fileList) => {
  const root = { kind: 'directory', name: 'root', children: new Map(), path: [] };
  for (const file of fileList) {
    const parts = (file.webkitRelativePath || file.name).split('/').filter(Boolean);
    let node = root;
    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1;
      if (!node.children.has(part)) {
        node.children.set(part, {
          kind: isFile ? 'file' : 'directory',
          name: part,
          children: isFile ? undefined : new Map(),
          file: isFile ? file : undefined,
          path: parts.slice(0, index + 1),
          lastModified: isFile ? file.lastModified : null,
          size: isFile ? file.size : 0,
        });
      }
      node = node.children.get(part);
    });
  }
  return root;
};

const getFallbackNode = (path) => {
  let node = state.fallbackTree;
  for (const segment of path) {
    node = node?.children?.get(segment);
  }
  return node;
};

const getCurrentDirectoryLabel = () => (state.currentPath.length ? state.currentPath.join('/') : '根目录');

const getEntryIcon = (entry) => {
  if (entry.kind === 'directory') return '📁';
  const ext = entry.name.split('.').pop()?.toLowerCase();
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return '🖼️';
  if (['json'].includes(ext)) return '🧩';
  if (['txt', 'md', 'csv', 'js', 'ts', 'py', 'html', 'css'].includes(ext)) return '📄';
  return '🗂️';
};

const readFileText = async (entry) => {
  if (entry.file) {
    return entry.file.text();
  }
  const file = await entry.handle.getFile();
  return file.text();
};

const getEntryFile = async (entry) => {
  if (entry.file) return entry.file;
  return entry.handle.getFile();
};

const previewEntry = async (entry) => {
  state.selectedEntry = entry;
  dom.selectionCount.textContent = entry ? `已选择：${entry.name}` : '未选择';
  dom.previewKind.textContent = entry ? (entry.kind === 'directory' ? '文件夹' : '文件') : '暂无内容';

  if (!entry) {
    dom.previewMeta.textContent = '选择一个文件或文件夹查看详情。';
    dom.previewContent.className = 'preview-content empty-state';
    dom.previewContent.textContent = '打开目录后，点击右侧列表中的项目即可预览。';
    return;
  }

  dom.previewMeta.innerHTML = [
    `<div><strong>名称：</strong>${entry.name}</div>`,
    `<div><strong>类型：</strong>${entry.kind === 'directory' ? '文件夹' : '文件'}</div>`,
    `<div><strong>路径：</strong>/${entry.path.join('/')}</div>`,
    `<div><strong>大小：</strong>${formatBytes(entry.size)}</div>`,
    `<div><strong>修改时间：</strong>${formatDate(entry.lastModified)}</div>`,
  ].join('');

  dom.previewContent.className = 'preview-content';

  if (entry.kind === 'directory') {
    dom.previewContent.innerHTML = `<div class="empty-state">文件夹 <strong>${entry.name}</strong> 已准备好，可点击“打开”进入。</div>`;
    return;
  }

  const ext = entry.name.split('.').pop()?.toLowerCase();
  const file = await getEntryFile(entry);

  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) {
    const url = URL.createObjectURL(file);
    dom.previewContent.innerHTML = `<img src="${url}" alt="${entry.name}" />`;
    return;
  }

  if (['txt', 'md', 'csv', 'json', 'js', 'ts', 'py', 'html', 'css'].includes(ext)) {
    const text = await readFileText(entry);
    dom.previewContent.innerHTML = `<pre>${escapeHtml(text.slice(0, 20000))}</pre>`;
    return;
  }

  dom.previewContent.innerHTML = `<div class="empty-state">该文件类型暂不支持在线预览，可使用“下载”导出。</div>`;
};

const escapeHtml = (value) =>
  value.replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;',
  }[char]));

const sortEntries = (entries) => {
  const mode = dom.sortSelect.value;
  const list = [...entries];
  const compareName = (a, b) => a.name.localeCompare(b.name, 'zh-CN');

  list.sort((a, b) => {
    if (mode === 'folders-first') {
      if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1;
      return compareName(a, b);
    }
    if (mode === 'name-asc') return compareName(a, b);
    if (mode === 'name-desc') return compareName(b, a);
    if (mode === 'size-desc') return (b.size || 0) - (a.size || 0) || compareName(a, b);
    if (mode === 'size-asc') return (a.size || 0) - (b.size || 0) || compareName(a, b);
    return 0;
  });

  return list;
};

const renderBreadcrumbs = () => {
  dom.breadcrumbs.innerHTML = '';
  const parts = [{ label: '根目录', path: [] }];
  state.currentPath.forEach((segment, index) => {
    parts.push({ label: segment, path: state.currentPath.slice(0, index + 1) });
  });

  parts.forEach((item, index) => {
    const button = document.createElement('button');
    button.textContent = item.label;
    button.addEventListener('click', () => navigateTo(item.path));
    dom.breadcrumbs.appendChild(button);
    if (index < parts.length - 1) {
      const separator = document.createElement('span');
      separator.textContent = '/';
      separator.className = 'muted';
      dom.breadcrumbs.appendChild(separator);
    }
  });
};

const createActionButtons = (entry) => {
  const fragment = dom.actionTemplate.content.cloneNode(true);
  fragment.querySelectorAll('button').forEach((button) => {
    button.addEventListener('click', async (event) => {
      event.stopPropagation();
      const action = button.dataset.action;
      if (action === 'open') {
        if (entry.kind === 'directory') {
          await navigateTo(entry.path);
        } else {
          await previewEntry(entry);
        }
      }
      if (action === 'rename') await renameEntry(entry);
      if (action === 'download') await downloadEntry(entry);
      if (action === 'delete') await deleteEntry(entry);
    });
  });
  return fragment;
};

const renderEntries = () => {
  const query = dom.searchInput.value.trim().toLowerCase();
  const filtered = sortEntries(
    state.entries.filter((entry) => !query || entry.name.toLowerCase().includes(query))
  );

  dom.fileList.innerHTML = '';

  if (!filtered.length) {
    const row = document.createElement('tr');
    row.innerHTML = '<td colspan="5" class="muted">当前目录暂无匹配项目。</td>';
    dom.fileList.appendChild(row);
    updateStats();
    return;
  }

  filtered.forEach((entry) => {
    const row = document.createElement('tr');
    row.className = state.selectedEntry?.path?.join('/') === entry.path.join('/') ? 'selected' : '';
    row.addEventListener('click', () => previewEntry(entry).then(renderEntries));

    const nameCell = document.createElement('td');
    nameCell.innerHTML = `
      <div class="name-cell">
        <span class="icon">${getEntryIcon(entry)}</span>
        <div>
          <strong>${entry.name}</strong>
          <div class="muted">/${entry.path.join('/')}</div>
        </div>
      </div>`;

    const typeCell = document.createElement('td');
    typeCell.textContent = entry.kind === 'directory' ? '文件夹' : '文件';

    const sizeCell = document.createElement('td');
    sizeCell.textContent = entry.kind === 'directory' ? '—' : formatBytes(entry.size);

    const dateCell = document.createElement('td');
    dateCell.textContent = formatDate(entry.lastModified);

    const actionCell = document.createElement('td');
    actionCell.appendChild(createActionButtons(entry));

    row.append(nameCell, typeCell, sizeCell, dateCell, actionCell);
    dom.fileList.appendChild(row);
  });

  updateStats();
};

const listFallbackEntries = (path) => {
  const node = getFallbackNode(path) || state.fallbackTree;
  const children = [...(node.children?.values() || [])];
  return children.map((child) => ({
    ...child,
    handle: null,
    lastModified: child.lastModified,
    size: child.size,
  }));
};

const listRealEntries = async () => {
  const entries = [];
  for await (const handle of state.currentHandle.values()) {
    const entry = {
      handle,
      kind: handle.kind,
      name: handle.name,
      path: [...state.currentPath, handle.name],
      size: 0,
      lastModified: null,
    };
    if (handle.kind === 'file') {
      const file = await handle.getFile();
      entry.size = file.size;
      entry.lastModified = file.lastModified;
    }
    entries.push(entry);
  }
  return entries;
};

const refreshEntries = async () => {
  state.entries = state.supportsFSAccess ? await listRealEntries() : listFallbackEntries(state.currentPath);
  renderBreadcrumbs();
  renderEntries();
  setActionAvailability(true);
  setStatus(`当前正在查看：${getCurrentDirectoryLabel()}`);
};

const navigateTo = async (path) => {
  state.currentPath = [...path];
  if (state.supportsFSAccess) {
    let handle = state.rootHandle;
    for (const segment of path) {
      handle = await handle.getDirectoryHandle(segment);
    }
    state.currentHandle = handle;
  }
  await refreshEntries();
};

const openDirectory = async () => {
  if (!state.supportsFSAccess) {
    setStatus('当前浏览器不支持直接访问本地文件系统，请使用“回退导入目录”。');
    return;
  }

  state.rootHandle = await window.showDirectoryPicker();
  state.currentHandle = state.rootHandle;
  state.currentPath = [];
  state.selectedEntry = null;
  await refreshEntries();
};

const loadFallbackDirectory = async (files) => {
  state.fallbackTree = buildFallbackTree(files);
  state.currentPath = [];
  state.selectedEntry = null;
  state.entries = listFallbackEntries([]);
  renderBreadcrumbs();
  renderEntries();
  setActionAvailability(true);
  setStatus('已通过回退模式导入目录。该模式支持浏览与预览，不支持直接写回本地磁盘。');
};

const renameEntry = async (entry) => {
  if (!state.supportsFSAccess) {
    alert('回退模式下无法直接重命名，请使用支持 File System Access API 的浏览器。');
    return;
  }

  const nextName = window.prompt('请输入新的名称：', entry.name)?.trim();
  if (!nextName || nextName === entry.name) return;

  if (entry.kind === 'directory') {
    const newDir = await state.currentHandle.getDirectoryHandle(nextName, { create: true });
    const sourceDir = await state.currentHandle.getDirectoryHandle(entry.name);
    for await (const child of sourceDir.values()) {
      if (child.kind === 'file') {
        const sourceFile = await child.getFile();
        const target = await newDir.getFileHandle(child.name, { create: true });
        const writable = await target.createWritable();
        await writable.write(await sourceFile.arrayBuffer());
        await writable.close();
      }
    }
    await state.currentHandle.removeEntry(entry.name, { recursive: true });
  } else {
    const sourceFile = await entry.handle.getFile();
    const targetHandle = await state.currentHandle.getFileHandle(nextName, { create: true });
    const writable = await targetHandle.createWritable();
    await writable.write(await sourceFile.arrayBuffer());
    await writable.close();
    await state.currentHandle.removeEntry(entry.name);
  }

  await refreshEntries();
  setStatus(`已将 ${entry.name} 重命名为 ${nextName}`);
};

const downloadEntry = async (entry) => {
  if (entry.kind === 'directory') {
    alert('目录下载可在后续迭代中加入压缩导出，这个版本先支持单文件下载。');
    return;
  }

  const file = await getEntryFile(entry);
  const url = URL.createObjectURL(file);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = entry.name;
  anchor.click();
  URL.revokeObjectURL(url);
};

const deleteEntry = async (entry) => {
  if (!state.supportsFSAccess) {
    alert('回退模式下无法删除本地项目。');
    return;
  }

  const ok = window.confirm(`确定删除 ${entry.name} 吗？该操作不可撤销。`);
  if (!ok) return;

  await state.currentHandle.removeEntry(entry.name, { recursive: entry.kind === 'directory' });
  if (state.selectedEntry?.path?.join('/') === entry.path.join('/')) {
    state.selectedEntry = null;
  }
  await refreshEntries();
  await previewEntry(null);
  setStatus(`已删除 ${entry.name}`);
};

const createFolder = async () => {
  const folderName = window.prompt('请输入新文件夹名称：')?.trim();
  if (!folderName) return;
  await state.currentHandle.getDirectoryHandle(folderName, { create: true });
  await refreshEntries();
  setStatus(`已创建文件夹 ${folderName}`);
};

const createFile = async () => {
  const fileName = window.prompt('请输入文件名（例如 notes.txt）：', 'notes.txt')?.trim();
  if (!fileName) return;
  const handle = await state.currentHandle.getFileHandle(fileName, { create: true });
  const writable = await handle.createWritable();
  await writable.write(`新建于 ${new Date().toLocaleString('zh-CN')}\n`);
  await writable.close();
  await refreshEntries();
  setStatus(`已创建文件 ${fileName}`);
};

const uploadFiles = async (files) => {
  if (!state.supportsFSAccess) {
    alert('回退模式不支持上传写回，请使用现代 Chromium 浏览器。');
    return;
  }
  for (const file of files) {
    const handle = await state.currentHandle.getFileHandle(file.name, { create: true });
    const writable = await handle.createWritable();
    await writable.write(await file.arrayBuffer());
    await writable.close();
  }
  await refreshEntries();
  setStatus(`已上传 ${files.length} 个文件到 ${getCurrentDirectoryLabel()}`);
};

const attachEvents = () => {
  dom.openDirectoryBtn.addEventListener('click', () => openDirectory().catch(handleError));
  dom.directoryPicker.addEventListener('change', (event) => loadFallbackDirectory([...event.target.files]).catch(handleError));
  dom.searchInput.addEventListener('input', renderEntries);
  dom.sortSelect.addEventListener('change', renderEntries);
  dom.refreshBtn.addEventListener('click', () => refreshEntries().catch(handleError));
  dom.newFolderBtn.addEventListener('click', () => createFolder().catch(handleError));
  dom.newFileBtn.addEventListener('click', () => createFile().catch(handleError));

  ['dragenter', 'dragover'].forEach((type) => {
    dom.dropZone.addEventListener(type, (event) => {
      event.preventDefault();
      dom.dropZone.classList.add('dragging');
    });
  });

  ['dragleave', 'drop'].forEach((type) => {
    dom.dropZone.addEventListener(type, (event) => {
      event.preventDefault();
      dom.dropZone.classList.remove('dragging');
    });
  });

  dom.dropZone.addEventListener('drop', async (event) => {
    const files = [...event.dataTransfer.files];
    if (files.length) {
      await uploadFiles(files).catch(handleError);
    }
  });
};

const handleError = (error) => {
  console.error(error);
  setStatus(`发生错误：${error.message}`);
  alert(`操作失败：${error.message}`);
};

const init = () => {
  attachEvents();
  setActionAvailability(false);
  dom.currentPath.textContent = '未打开';
  if (!state.supportsFSAccess) {
    setStatus('当前浏览器缺少 File System Access API，建议使用 Chrome / Edge；仍可使用回退导入模式浏览目录。');
  }
};

init();
