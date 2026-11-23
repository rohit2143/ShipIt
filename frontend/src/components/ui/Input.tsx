import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  mono?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      label,
      error,
      helperText,
      leftIcon,
      rightIcon,
      mono = false,
      disabled,
      id,
      ...props
    },
    ref
  ) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="w-full space-y-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-xs font-medium text-slate-300"
          >
            {label}
          </label>
        )}

        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute left-3 text-slate-500 pointer-events-none flex items-center">
              {leftIcon}
            </div>
          )}

          <input
            id={inputId}
            ref={ref}
            disabled={disabled}
            className={twMerge(
              clsx(
                'w-full py-2 px-3 text-xs rounded-lg bg-[#080B14] border text-slate-100 placeholder:text-slate-500 transition-all duration-150',
                'focus:outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20',
                'disabled:opacity-50 disabled:bg-[#0D1120] disabled:cursor-not-allowed',
                leftIcon ? 'pl-9' : 'pl-3',
                rightIcon ? 'pr-9' : 'pr-3',
                mono && 'font-mono',
                error ? 'border-rose-500/80 focus:border-rose-500 focus:ring-rose-500/20' : 'border-[#252D46]',
                className
              )
            )}
            {...props}
          />

          {rightIcon && (
            <div className="absolute right-3 text-slate-500 flex items-center">
              {rightIcon}
            </div>
          )}
        </div>

        {error ? (
          <p className="text-[11px] text-rose-400 font-medium">{error}</p>
        ) : helperText ? (
          <p className="text-[11px] text-slate-500">{helperText}</p>
        ) : null}
      </div>
    );
  }
);

Input.displayName = 'Input';
