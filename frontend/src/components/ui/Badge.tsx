import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'success' | 'purple' | 'blue' | 'cyan' | 'warning' | 'danger' | 'neutral';
  size?: 'xs' | 'sm' | 'md';
  dot?: boolean;
  pulse?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({
  className,
  variant = 'neutral',
  size = 'sm',
  dot = false,
  pulse = false,
  children,
  ...props
}) => {
  const variants = {
    success: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/25',
    purple: 'bg-purple-500/10 text-purple-300 border-purple-500/30',
    blue: 'bg-blue-500/10 text-blue-400 border-blue-500/25',
    cyan: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/25',
    warning: 'bg-amber-500/10 text-amber-400 border-amber-500/25',
    danger: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
    neutral: 'bg-slate-800/80 text-slate-300 border-[#252D46]',
  };

  const dotColors = {
    success: 'bg-emerald-400',
    purple: 'bg-purple-400 shadow-[0_0_8px_rgba(167,139,250,0.8)]',
    blue: 'bg-blue-400 shadow-[0_0_8px_rgba(59,130,246,0.8)]',
    cyan: 'bg-cyan-400 shadow-[0_0_8px_rgba(6,182,212,0.8)]',
    warning: 'bg-amber-400',
    danger: 'bg-rose-400',
    neutral: 'bg-slate-400',
  };

  const sizes = {
    xs: 'text-[10px] px-1.5 py-0.5 rounded font-mono gap-1',
    sm: 'text-[11px] px-2 py-0.5 rounded-md font-mono gap-1.5',
    md: 'text-xs px-2.5 py-1 rounded-md font-medium gap-1.5',
  };

  return (
    <span
      className={twMerge(
        clsx(
          'inline-flex items-center border font-medium select-none',
          variants[variant],
          sizes[size],
          className
        )
      )}
      {...props}
    >
      {dot && (
        <span
          className={clsx(
            'w-1.5 h-1.5 rounded-full shrink-0',
            dotColors[variant],
            pulse && 'animate-pulse'
          )}
        />
      )}
      <span>{children}</span>
    </span>
  );
};
