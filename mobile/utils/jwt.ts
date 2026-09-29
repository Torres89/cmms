export const JWT_EXPIRES_IN = 3600 * 24 * 2;

const decodeBase64Url = (part: string): string => {
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
  return atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
};

export const verify = async (token: string): Promise<boolean> => {
  try {
    const payload = JSON.parse(decodeBase64Url(token.split('.')[1]));
    const currentTime = new Date().getTime() / 1000;
    return currentTime <= payload.exp;
  } catch (error) {
    // Token is invalid or expired
    return false;
  }
};
