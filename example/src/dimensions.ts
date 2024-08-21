import { Dimensions } from 'react-native';

const { width, height } = Dimensions.get('window');
const orientation = width > height ? 'LANDSCAPE' : 'PORTRAIT';

function isLandscape(
  v: string | undefined,
  v2: string | undefined = undefined
) {
  return orientation === 'LANDSCAPE' ? v : v2;
}

export { orientation, width, height, isLandscape };
