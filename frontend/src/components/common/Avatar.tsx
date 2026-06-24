import * as React from "react";
import * as AvatarPrimitive from "@radix-ui/react-avatar";

import { cn } from "@/lib/utils";
import { getEmojiAvatar } from "@/utils/emojiAvatar";

interface AvatarProps {
  name: string;
  src?: string;
  className?: string;
}

export function Avatar({ name, src, className }: AvatarProps) {
  const emoji = React.useMemo(() => getEmojiAvatar(name), [name]);

  return (
    <AvatarPrimitive.Root
      className={cn(
        "inline-flex h-9 w-9 select-none items-center justify-center overflow-hidden rounded-full border border-[#F5E9DC] bg-[#FFEDD5] text-base leading-none",
        className
      )}
    >
      {src ? (
        <AvatarPrimitive.Image src={src} alt={name} className="h-full w-full rounded-full object-cover" />
      ) : null}
      <AvatarPrimitive.Fallback className="leading-none">{emoji}</AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );
}
