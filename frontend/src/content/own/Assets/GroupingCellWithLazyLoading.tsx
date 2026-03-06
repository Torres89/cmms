import * as React from 'react';
import { GridRenderCellParams } from '@mui/x-data-grid';
import Box from '@mui/material/Box';

interface GroupingCellWithLazyLoadingProps extends GridRenderCellParams {}

export function GroupingCellWithLazyLoading(
  props: GroupingCellWithLazyLoadingProps
) {
  return (
    <Box>
      <span />
    </Box>
  );
}
