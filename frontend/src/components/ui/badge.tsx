import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center rounded-lg border px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default:
          'border-teal-500/20 bg-teal-500/10 text-teal-400',
        secondary:
          'border-white/5 bg-white/5 text-muted-foreground',
        destructive:
          'border-red-500/20 bg-red-500/10 text-red-400',
        outline:
          'border-white/10 text-muted-foreground',
        accent:
          'border-purple-500/20 bg-purple-500/10 text-purple-400',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge, badgeVariants };
