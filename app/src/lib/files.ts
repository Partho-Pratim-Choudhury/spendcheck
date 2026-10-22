import { File } from 'expo-file-system';

/** Best-effort delete of a local file (e.g. a voice recording). Never throws. */
export function deleteLocalFile(uri: string | null | undefined): void {
  if (!uri) return;
  try {
    const file = new File(uri);
    if (file.exists) file.delete();
  } catch {
    // Already gone or not ours to delete — nothing else to do.
  }
}
