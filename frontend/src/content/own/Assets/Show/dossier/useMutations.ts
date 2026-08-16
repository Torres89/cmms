import { useCallback, useContext } from 'react';
import { useTranslation } from 'react-i18next';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../../utils/api';

/**
 * The one thing every dossier tab does identically: run a mutation, tell the
 * user how it went, reload so derived numbers cannot drift, and rethrow.
 *
 * Rethrowing matters. A dialog that closes on a failed save throws away what
 * the user typed, so `run` never swallows the error — the caller decides
 * whether to close.
 *
 * This is deliberately the only shared abstraction across the tabs. Their data
 * is a tree, quantity lines, uploads, timeline events and readings; there is no
 * common resource underneath, and pretending otherwise would buy five escape
 * hatches.
 */
const useMutations = (reload: () => Promise<any> | void) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);

  const run = useCallback(
    <T>(
      promise: Promise<T>,
      successKey: string,
      errorKey: string
    ): Promise<T> =>
      promise
        .then(async (result) => {
          showSnackBar(t(successKey), 'success');
          await reload();
          return result;
        })
        .catch((error) => {
          // The server's own message when it bothered to send one — "Asset with
          // same barCode exists" is far more use than "could not save".
          showSnackBar(getErrorMessage(error, t(errorKey)) ?? t(errorKey), 'error');
          throw error;
        }),
    [reload, showSnackBar, t]
  );

  return { run };
};

export default useMutations;
