import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'elevated' | 'glass' | 'interactive';
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  ({ className, variant = 'default', children, ...props }, ref) => {
    const variants = {
      default: 'bg-[#111629] border border-[#252D46] rounded-xl shadow-sm',
      elevated: 'bg-[#171E34] border border-[#252D46] rounded-xl shadow-lg',
      glass: 'glass-panel rounded-xl shadow-sm',
      interactive:
        'bg-[#111629] border border-[#252D46] rounded-xl shadow-sm hover:border-purple-500/40 hover:shadow-[0_0_24px_-4px_rgba(139,92,246,0.15)] transition-all duration-200 cursor-pointer',
    };

    return (
      <div
        ref={ref}
        className={twMerge(clsx(variants[variant], className))}
        {...props}
      >
        {children}
      </div>
    );
  }
);

Card.displayName = 'Card';

export const CardHeader = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={twMerge(clsx('flex flex-col space-y-1.5 p-5 pb-3 border-b border-[#252D46]', className))}
    {...props}
  />
));
CardHeader.displayName = 'CardHeader';

export const CardTitle = React.forwardRef<
  HTMLHeadingElement,
  React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h3
    ref={ref}
    className={twMerge(
      clsx('text-sm font-bold text-slate-100 tracking-tight flex items-center gap-2', className)
    )}
    {...props}
  />
));
CardTitle.displayName = 'CardTitle';

export const CardDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p
    ref={ref}
    className={twMerge(clsx('text-xs text-slate-400 mt-0.5', className))}
    {...props}
  />
));
CardDescription.displayName = 'CardDescription';

export const CardContent = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div ref={ref} className={twMerge(clsx('p-5', className))} {...props} />
));
CardContent.displayName = 'CardContent';

export const CardFooter = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={twMerge(
      clsx('flex items-center p-4 border-t border-[#252D46] bg-[#0D1120]/50 rounded-b-xl', className)
    )}
    {...props}
  />
));
CardFooter.displayName = 'CardFooter';
