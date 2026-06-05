import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 active:scale-[0.98]',
  {
    variants: {
      variant: {
        default:
          'bg-teal-500 text-teal-950 shadow-sm hover:bg-teal-400 hover:shadow-md hover:shadow-teal-500/20',
        destructive:
          'bg-red-500/90 text-white shadow-sm hover:bg-red-500 hover:shadow-md hover:shadow-red-500/20',
        outline:
          'border border-white/10 bg-white/5 backdrop-blur-sm hover:bg-white/10 hover:border-white/20 text-foreground',
        secondary:
          'bg-white/10 text-foreground hover:bg-white/15',
        ghost:
          'hover:bg-white/10 text-muted-foreground hover:text-foreground',
        link: 'text-teal-400 underline-offset-4 hover:underline',
        gradient:
          'bg-gradient-to-r from-teal-500 to-cyan-500 text-teal-950 shadow-md hover:shadow-lg hover:shadow-teal-500/25 hover:from-teal-400 hover:to-cyan-400 font-semibold',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-11 rounded-lg px-6 text-base',
        icon: 'h-9 w-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = 'Button';

export { Button, buttonVariants };
