import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertOctagon, AlertTriangle, Info, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

interface ToastContextType {
  toast: {
    success: (message: string, duration?: number) => void;
    error: (message: string, duration?: number) => void;
    warning: (message: string, duration?: number) => void;
    info: (message: string, duration?: number) => void;
  };
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback((message: string, type: ToastType = 'info', duration = 4000) => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, message, type, duration }]);

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }, [removeToast]);

  const toast = {
    success: (msg: string, duration?: number) => addToast(msg, 'success', duration),
    error: (msg: string, duration?: number) => addToast(msg, 'error', duration),
    warning: (msg: string, duration?: number) => addToast(msg, 'warning', duration),
    info: (msg: string, duration?: number) => addToast(msg, 'info', duration),
  };

  const icons = {
    success: <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />,
    error: <AlertOctagon className="w-4 h-4 text-rose-400 shrink-0" />,
    warning: <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />,
    info: <Info className="w-4 h-4 text-cyan-400 shrink-0" />,
  };

  const borders = {
    success: 'border-emerald-500/30 shadow-[0_4px_20px_rgba(16,185,129,0.15)]',
    error: 'border-rose-500/30 shadow-[0_4px_20px_rgba(239,68,68,0.15)]',
    warning: 'border-amber-500/30 shadow-[0_4px_20px_rgba(245,158,11,0.15)]',
    info: 'border-cyan-500/30 shadow-[0_4px_20px_rgba(6,182,212,0.15)]',
  };

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}

      {/* Toast viewport container */}
      <div
        aria-live="polite"
        className="fixed bottom-4 right-4 z-[9999] flex flex-col space-y-2 max-w-sm w-full pointer-events-none p-2"
      >
        {toasts.map((item) => (
          <div
            key={item.id}
            className={`pointer-events-auto flex items-start justify-between gap-3 p-3.5 rounded-xl bg-[#111629]/95 backdrop-blur-md border text-xs text-slate-100 transition-all duration-200 transform translate-y-0 ${
              borders[item.type]
            }`}
          >
            <div className="flex items-start gap-2.5">
              {icons[item.type]}
              <span className="font-medium leading-relaxed">{item.message}</span>
            </div>
            <button
              onClick={() => removeToast(item.id)}
              className="text-slate-500 hover:text-slate-300 transition-colors p-0.5 cursor-pointer -mr-1 -mt-0.5"
              aria-label="Dismiss notification"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = (): ToastContextType['toast'] => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context.toast;
};
