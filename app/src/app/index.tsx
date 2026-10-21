import { Redirect } from 'expo-router';

import { useAuth } from '@/lib/auth';

/** Entry route "/": send the user to the initial tab (Chat) or to login. */
export default function Index() {
  const { status } = useAuth();
  return <Redirect href={status === 'signedIn' ? '/chat' : '/login'} />;
}
