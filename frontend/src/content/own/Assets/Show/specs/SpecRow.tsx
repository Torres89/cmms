import { FC, useState } from 'react';
import {
  Box,
  Checkbox,
  FormControlLabel,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import CloseIcon from '@mui/icons-material/Close';
import DoneIcon from '@mui/icons-material/Done';
import { AssetSpec } from '../../../../../models/owns/dossier';
import { SpecValueUpdate } from './useAssetSpecs';

interface PropsType {
  spec: AssetSpec;
  canEdit: boolean;
  onSave: (id: number, changes: SpecValueUpdate) => Promise<any>;
  onDelete: (spec: AssetSpec) => void;
  onVerify: (spec: AssetSpec) => void;
  onUnverify: (spec: AssetSpec) => void;
}

const displayValue = (spec: AssetSpec): string => {
  if (spec.valueText !== null && spec.valueText !== undefined)
    return spec.valueText;
  if (spec.valueNum !== null && spec.valueNum !== undefined)
    return String(spec.valueNum);
  return '';
};

/**
 * One spec value. Editing happens in place and covers every field.
 *
 * The verify checkbox is the whole provenance story in one control: an
 * unverified value is something a machine proposed, and ticking the box is a
 * person putting their name to it. Who did that, and when, stays on the card.
 */
const SpecRow: FC<PropsType> = ({
  spec,
  canEdit,
  onSave,
  onDelete,
  onVerify,
  onUnverify
}) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draftLabel, setDraftLabel] = useState('');
  const [draftValue, setDraftValue] = useState('');
  const [draftUnit, setDraftUnit] = useState('');
  const [draftGroup, setDraftGroup] = useState('');
  const [draftKey, setDraftKey] = useState('');

  const startEditing = () => {
    setDraftLabel(spec.label ?? '');
    setDraftValue(displayValue(spec));
    setDraftUnit(spec.unit ?? '');
    setDraftGroup(spec.specGroup ?? '');
    setDraftKey(spec.specKey ?? '');
    setEditing(true);
  };

  const save = () => {
    const value = draftValue.trim();
    // A value that reads as a number is stored as one, whatever it was before.
    const asNumber = value !== '' && !Number.isNaN(Number(value));
    setSaving(true);
    onSave(spec.id, {
      specKey: draftKey.trim() || spec.specKey,
      specGroup: draftGroup.trim() || t('general'),
      label: draftLabel.trim() || draftKey.trim() || spec.specKey,
      unit: draftUnit.trim(),
      valueText: asNumber || value === '' ? null : value,
      valueNum: asNumber ? Number(value) : null
    })
      .then(() => setEditing(false))
      // The hook already raised a snackbar. Staying in edit mode keeps the
      // user's input rather than throwing it away on a transient failure.
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  const verifiedByName = spec.verifiedBy
    ? `${spec.verifiedBy.firstName ?? ''} ${
        spec.verifiedBy.lastName ?? ''
      }`.trim()
    : '';

  const sourceHint = spec.sourceDocument
    ? `${t('from')} ${spec.sourceDocument.title}${
        spec.sourcePage ? ` p. ${spec.sourcePage}` : ''
      }`
    : t('extracted_value');

  return (
    <Box
      sx={{
        p: 1.25,
        borderRadius: 1.5,
        border: `1px solid ${theme.colors.alpha.black[10]}`,
        height: '100%',
        '&:hover .spec-row-actions': { opacity: 1 }
      }}
    >
      {editing ? (
        <Stack spacing={1.25}>
          <TextField
            size="small"
            autoFocus
            fullWidth
            label={t('label')}
            value={draftLabel}
            onChange={(e) => setDraftLabel(e.target.value)}
            disabled={saving}
          />
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              fullWidth
              label={t('value')}
              value={draftValue}
              onChange={(e) => setDraftValue(e.target.value)}
              disabled={saving}
            />
            <TextField
              size="small"
              sx={{ width: 90 }}
              label={t('unit')}
              value={draftUnit}
              onChange={(e) => setDraftUnit(e.target.value)}
              disabled={saving}
            />
          </Stack>
          <TextField
            size="small"
            fullWidth
            label={t('spec_group')}
            value={draftGroup}
            onChange={(e) => setDraftGroup(e.target.value)}
            disabled={saving}
          />
          <TextField
            size="small"
            fullWidth
            label={t('spec_key')}
            value={draftKey}
            onChange={(e) => setDraftKey(e.target.value)}
            disabled={saving}
          />
          <Stack direction="row" justifyContent="flex-end">
            <Tooltip title={t('cancel')}>
              <IconButton
                size="small"
                onClick={() => setEditing(false)}
                disabled={saving}
              >
                <CloseIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('save')}>
              <IconButton
                size="small"
                color="primary"
                onClick={save}
                disabled={saving}
              >
                <DoneIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          </Stack>
        </Stack>
      ) : (
        <>
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="flex-start"
          >
            <Typography variant="caption" color="text.secondary">
              {spec.label ?? spec.specKey}
            </Typography>
            {canEdit && (
              <Stack
                direction="row"
                className="spec-row-actions"
                sx={{ opacity: { xs: 1, md: 0 }, transition: 'opacity 150ms' }}
              >
                <Tooltip title={t('edit')}>
                  <IconButton size="small" onClick={startEditing}>
                    <EditTwoToneIcon sx={{ fontSize: 15 }} />
                  </IconButton>
                </Tooltip>
                <Tooltip title={t('to_delete')}>
                  <IconButton size="small" onClick={() => onDelete(spec)}>
                    <DeleteTwoToneIcon sx={{ fontSize: 15 }} />
                  </IconButton>
                </Tooltip>
              </Stack>
            )}
          </Stack>

          <Typography variant="body1" sx={{ fontWeight: 600 }}>
            {spec.valueText ?? spec.valueNum?.toLocaleString() ?? '—'}
            {spec.unit && (
              <Typography
                component="span"
                variant="body2"
                color="text.secondary"
              >
                {' '}
                {spec.unit}
              </Typography>
            )}
          </Typography>

          <Tooltip title={spec.verified ? '' : sourceHint}>
            <FormControlLabel
              sx={{ mt: 0.25, ml: 0 }}
              control={
                <Checkbox
                  size="small"
                  checked={spec.verified}
                  disabled={!canEdit}
                  onChange={() =>
                    spec.verified ? onUnverify(spec) : onVerify(spec)
                  }
                  sx={{ p: 0.5 }}
                />
              }
              label={
                <Typography
                  variant="caption"
                  color={spec.verified ? 'text.secondary' : 'warning.main'}
                >
                  {spec.verified
                    ? `${t('verified')}${
                        verifiedByName ? ` · ${verifiedByName}` : ''
                      }${
                        spec.verifiedAt
                          ? ` · ${new Date(
                              spec.verifiedAt
                            ).toLocaleDateString()}`
                          : ''
                      }`
                    : t('verify')}
                </Typography>
              }
            />
          </Tooltip>
        </>
      )}
    </Box>
  );
};

export default SpecRow;
