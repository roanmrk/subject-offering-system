import React, { createContext, useContext, useState, useCallback, useRef } from 'react';
import './Toast.css';

// ============================================================
// TOAST CONTEXT
// ============================================================
const ToastContext = createContext(null);

// ============================================================
// PROVIDER — wraps the app, exposes toast + confirm
// ============================================================
export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);
  const [confirmState, setConfirmState] = useState(null);
  const toastIdRef = useRef(0);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((type, title, message = '', duration = 4000) => {
    const id = ++toastIdRef.current;
    const newToast = { id, type, title, message, duration };
    setToasts((prev) => [...prev, newToast]);
    if (duration > 0) {
      setTimeout(() => removeToast(id), duration);
    }
    return id;
  }, [removeToast]);

  const toast = {
    success: (title, message, duration) => showToast('success', title, message, duration),
    error:   (title, message, duration) => showToast('error', title, message, duration),
    warning: (title, message, duration) => showToast('warning', title, message, duration),
    info:    (title, message, duration) => showToast('info', title, message, duration),
  };

  const confirm = useCallback((options) => {
    return new Promise((resolve) => {
      setConfirmState({
        title: options.title || 'Are you sure?',
        message: options.message || '',
        confirmText: options.confirmText || 'Confirm',
        cancelText: options.cancelText || 'Cancel',
        variant: options.variant || 'info',
        resolve,
      });
    });
  }, []);

  const handleConfirm = (result) => {
    if (confirmState?.resolve) {
      confirmState.resolve(result);
    }
    setConfirmState(null);
  };

  return (
    <ToastContext.Provider value={{ toast, confirm }}>
      {children}

      <div className="toast-container">
        {toasts.map((t) => (
          <Toast key={t.id} toast={t} onClose={() => removeToast(t.id)} />
        ))}
      </div>

      {confirmState && (
        <ConfirmDialog
          state={confirmState}
          onConfirm={() => handleConfirm(true)}
          onCancel={() => handleConfirm(false)}
        />
      )}
    </ToastContext.Provider>
  );
};

// ============================================================
// HOOK
// ============================================================
export const useToast = () => {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
};

// ============================================================
// TOAST COMPONENT
// ============================================================
const Toast = ({ toast, onClose }) => {
  const config = {
    success: { icon: 'fa-check-circle' },
    error:   { icon: 'fa-times-circle' },
    warning: { icon: 'fa-exclamation-triangle' },
    info:    { icon: 'fa-info-circle' },
  }[toast.type] || { icon: 'fa-info-circle' };

  return (
    <div className={`toast toast-${toast.type}`} role="alert">
      <i className={`fas ${config.icon} toast-icon`}></i>
      <div className="toast-content">
        <div className="toast-title">{toast.title}</div>
        {toast.message && (
          <div className="toast-message">{toast.message}</div>
        )}
      </div>
      <button className="toast-close" onClick={onClose} aria-label="Dismiss">
        <i className="fas fa-times"></i>
      </button>
      {toast.duration > 0 && (
        <div
          className="toast-progress"
          style={{ animationDuration: `${toast.duration}ms` }}
        />
      )}
    </div>
  );
};

// ============================================================
// CONFIRM DIALOG COMPONENT
// ============================================================
const ConfirmDialog = ({ state, onConfirm, onCancel }) => {
  const iconMap = {
    info:    { icon: 'fa-question-circle',     cls: 'icon-info'    },
    warning: { icon: 'fa-exclamation-triangle', cls: 'icon-warning' },
    danger:  { icon: 'fa-exclamation-circle',   cls: 'icon-danger'  },
  };
  const { icon, cls } = iconMap[state.variant] || iconMap.info;

  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="confirm-header">
          <i className={`fas ${icon} confirm-icon ${cls}`}></i>
          <div>
            <div className="confirm-title">{state.title}</div>
            {state.message && (
              <div className="confirm-message">{state.message}</div>
            )}
          </div>
        </div>
        <div className="confirm-actions">
          <button className="confirm-btn confirm-btn-cancel" onClick={onCancel}>
            {state.cancelText}
          </button>
          <button
            className={`confirm-btn confirm-btn-confirm ${state.variant === 'danger' ? 'danger' : ''}`}
            onClick={onConfirm}
            autoFocus
          >
            {state.confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

// Default export for convenience
export default ToastProvider;