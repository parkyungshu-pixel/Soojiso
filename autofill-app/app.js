(function () {
  "use strict";

  const STORAGE_KEY = "autofill-list:items";

  const listEl = document.getElementById("list");
  const countEl = document.getElementById("count");
  const statusEl = document.getElementById("status");
  const nextValueEl = document.getElementById("next-value");
  const saveBtn = document.getElementById("save");
  const clearBtn = document.getElementById("clear");
  const fillBtn = document.getElementById("fill-btn");

  function loadItems() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  function saveItems(items) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }

  function parseText(text) {
    return text
      .split("\n")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  function render(items) {
    // Only overwrite the textarea if its current content doesn't match
    // (prevents cursor jumps while the user is typing).
    const current = parseText(listEl.value);
    const same =
      current.length === items.length &&
      current.every((v, i) => v === items[i]);
    if (!same) {
      listEl.value = items.join("\n");
    }

    countEl.textContent = `${items.length} item${items.length === 1 ? "" : "s"}`;

    if (items.length === 0) {
      nextValueEl.textContent = "— empty list —";
      nextValueEl.classList.add("is-empty");
      fillBtn.disabled = true;
    } else {
      nextValueEl.textContent = items[0];
      nextValueEl.classList.remove("is-empty");
      fillBtn.disabled = false;
    }
  }

  function flashStatus(msg, kind) {
    statusEl.textContent = msg;
    statusEl.className = "status" + (kind ? " " + kind : "");
    clearTimeout(flashStatus._t);
    flashStatus._t = setTimeout(() => {
      statusEl.textContent = "";
      statusEl.className = "status";
    }, 1600);
  }

  async function copyToClipboard(text) {
    // Modern API first
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (e) {
        // fall through to legacy path
      }
    }
    // Legacy fallback using a hidden textarea + execCommand
    try {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.top = "-1000px";
      ta.style.left = "-1000px";
      document.body.appendChild(ta);
      ta.select();
      ta.setSelectionRange(0, text.length);
      const ok = document.execCommand("copy");
      document.body.removeChild(ta);
      return ok;
    } catch (e) {
      return false;
    }
  }

  // --- Wire up events ---

  saveBtn.addEventListener("click", () => {
    const items = parseText(listEl.value);
    saveItems(items);
    render(items);
    flashStatus("Saved \u2713", "ok");
  });

  clearBtn.addEventListener("click", () => {
    if (!confirm("Clear the entire list?")) return;
    saveItems([]);
    render([]);
    flashStatus("Cleared", "warn");
  });

  fillBtn.addEventListener("click", async () => {
    const items = loadItems();
    if (items.length === 0) {
      flashStatus("Empty list", "warn");
      return;
    }
    const value = items[0];
    const ok = await copyToClipboard(value);
    if (!ok) {
      flashStatus("Copy failed", "warn");
      return;
    }

    const rest = items.slice(1);
    saveItems(rest);
    render(rest);

    // Brief button feedback
    const textEl = fillBtn.querySelector(".fab-text");
    const original = textEl.textContent;
    textEl.textContent = `Copied! ${rest.length} left`;
    fillBtn.classList.add("is-success");
    setTimeout(() => {
      textEl.textContent = original;
      fillBtn.classList.remove("is-success");
    }, 1400);

    flashStatus("Copied \u2713 now paste it", "ok");
  });

  // Auto-save on blur so users don't lose edits if they forget to tap Save
  listEl.addEventListener("blur", () => {
    const items = parseText(listEl.value);
    const existing = loadItems();
    const same =
      items.length === existing.length &&
      items.every((v, i) => v === existing[i]);
    if (!same) {
      saveItems(items);
      render(items);
      flashStatus("Auto-saved", "ok");
    }
  });

  // Keep the "next item" preview live as the user types
  listEl.addEventListener("input", () => {
    const items = parseText(listEl.value);
    countEl.textContent = `${items.length} item${items.length === 1 ? "" : "s"}`;
    if (items.length === 0) {
      nextValueEl.textContent = "— empty list —";
      nextValueEl.classList.add("is-empty");
    } else {
      nextValueEl.textContent = items[0];
      nextValueEl.classList.remove("is-empty");
    }
  });

  // Initial render
  render(loadItems());

  // Register service worker for offline support
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {
        /* ignore */
      });
    });
  }
})();
