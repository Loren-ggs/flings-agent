const AVATAR_EMOJIS = [
  "🦊", "🐼", "🐨", "🦁", "🐯", "🦄", "🐸", "🐙", "🦉", "🐧",
  "🐵", "🐶", "🐱", "🦝", "🐰", "🐹", "🐻", "🐲", "🦖", "🐳",
  "🐬", "🦋", "🌸", "🌟", "🔥", "🍊", "🍉", "🍑", "🌈", "🚀",
  "🎨", "🎮", "🧩", "🍿", "🌮", "🐢", "🦕", "🐝", "🦜", "🦩"
];

/**
 * 根据用户标识确定性地映射一个 emoji 头像。
 * 同一用户始终得到同一 emoji（看起来随机但稳定），无标识时回退到 🙂。
 */
export function getEmojiAvatar(seed?: string | null): string {
  if (!seed) {
    return "🙂";
  }
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return AVATAR_EMOJIS[hash % AVATAR_EMOJIS.length];
}
