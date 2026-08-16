import { FC, useEffect, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Link,
  Stack,
  Switch,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useDispatch, useSelector } from '../../../../../store';
import { getPartsMini } from '../../../../../slices/part';
import { PartMiniDTO } from '../../../../../models/owns/part';
import { BomLine } from '../../../../../models/owns/dossier';
import { BomLinePayload } from './useAssetBom';

interface PropsType {
  open: boolean;
  onClose: () => void;
  /** Set when editing an existing line. */
  line: BomLine | null;
  onSubmit: (payload: BomLinePayload) => Promise<any>;
  onCreatePart: (part: {
    name: string;
    manufacturer?: string;
    mpn?: string;
    unit?: string;
  }) => Promise<{ id: number; name: string }>;
}

const numberOrUndefined = (value: string): number | undefined =>
  value.trim() === '' ? undefined : Number(value.trim());

/**
 * A line on the bill of materials.
 *
 * The part picker falls back to creating a part, because documenting what a
 * machine takes usually happens before anyone has stocked it. A BOM you cannot
 * write until purchasing has caught up is a BOM nobody writes.
 */
const BomLineFormDialog: FC<PropsType> = ({
  open,
  onClose,
  line,
  onSubmit,
  onCreatePart
}) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { partsMini } = useSelector((state) => state.parts);

  const [creatingPart, setCreatingPart] = useState(false);
  const [part, setPart] = useState<PartMiniDTO | null>(null);
  const [newPartName, setNewPartName] = useState('');
  const [newPartManufacturer, setNewPartManufacturer] = useState('');
  const [newPartMpn, setNewPartMpn] = useState('');
  const [newPartUnit, setNewPartUnit] = useState('');

  const [positionCode, setPositionCode] = useState('');
  const [qtyPerAssembly, setQtyPerAssembly] = useState('1');
  const [consumable, setConsumable] = useState(false);
  const [intervalHours, setIntervalHours] = useState('');
  const [intervalMonths, setIntervalMonths] = useState('');
  const [notes, setNotes] = useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    dispatch(getPartsMini());
    setCreatingPart(false);
    setPart(null);
    setNewPartName('');
    setNewPartManufacturer('');
    setNewPartMpn('');
    setNewPartUnit('');
    setPositionCode(line?.positionCode ?? '');
    setQtyPerAssembly(String(line?.qtyPerAssembly ?? 1));
    setConsumable(line?.consumable ?? false);
    setIntervalHours(
      line?.replaceIntervalHours != null ? String(line.replaceIntervalHours) : ''
    );
    setIntervalMonths(
      line?.replaceIntervalMonths != null
        ? String(line.replaceIntervalMonths)
        : ''
    );
    setNotes(line?.notes ?? '');
    setError(null);
    setSaving(false);
  }, [open, line]);

  // Preselect the line's part once the mini list has arrived.
  useEffect(() => {
    if (!open || !line?.part) return;
    const match = partsMini.find((candidate) => candidate.id === line.part.id);
    if (match) setPart(match);
  }, [open, line, partsMini]);

  const submit = async () => {
    const qty = numberOrUndefined(qtyPerAssembly);
    if (qty != null && Number.isNaN(qty)) {
      setError(t('value_must_be_a_number'));
      return;
    }

    setSaving(true);
    try {
      let partId = part?.id;
      if (creatingPart) {
        const name = newPartName.trim();
        if (!name) {
          setError(t('part_required'));
          setSaving(false);
          return;
        }
        const created = await onCreatePart({
          name,
          manufacturer: newPartManufacturer.trim() || undefined,
          mpn: newPartMpn.trim() || undefined,
          unit: newPartUnit.trim() || undefined
        });
        partId = created.id;
      }
      if (!partId) {
        setError(t('part_required'));
        setSaving(false);
        return;
      }

      await onSubmit({
        partId,
        positionCode: positionCode.trim() || undefined,
        qtyPerAssembly: qty,
        consumable,
        replaceIntervalHours: numberOrUndefined(intervalHours),
        replaceIntervalMonths: numberOrUndefined(intervalMonths),
        notes: notes.trim() || undefined
      });
      onClose();
    } catch {
      // The hook raised a snackbar; keep the dialog open with the input intact.
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">
          {line ? t('edit_bom_line') : t('add_bom_line')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {!creatingPart ? (
            <>
              <Autocomplete<PartMiniDTO, false, false, false>
                options={partsMini}
                value={part}
                onChange={(_event, value) => {
                  setPart(value);
                  setError(null);
                }}
                getOptionLabel={(option) => option.name}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                renderInput={(params) => (
                  <TextField {...params} label={t('part')} autoFocus />
                )}
              />
              <Link
                component="button"
                type="button"
                variant="body2"
                onClick={() => {
                  setCreatingPart(true);
                  setPart(null);
                  setError(null);
                }}
                sx={{ alignSelf: 'flex-start' }}
              >
                {t('create_new_part')}
              </Link>
            </>
          ) : (
            <>
              <TextField
                label={t('name')}
                value={newPartName}
                onChange={(event) => {
                  setNewPartName(event.target.value);
                  setError(null);
                }}
                fullWidth
                autoFocus
                required
              />
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('manufacturer')}
                  value={newPartManufacturer}
                  onChange={(event) =>
                    setNewPartManufacturer(event.target.value)
                  }
                  fullWidth
                />
                <TextField
                  label="MPN"
                  value={newPartMpn}
                  onChange={(event) => setNewPartMpn(event.target.value)}
                  fullWidth
                />
                <TextField
                  label={t('unit')}
                  value={newPartUnit}
                  onChange={(event) => setNewPartUnit(event.target.value)}
                  sx={{ width: 110 }}
                />
              </Stack>
              <Link
                component="button"
                type="button"
                variant="body2"
                onClick={() => {
                  setCreatingPart(false);
                  setError(null);
                }}
                sx={{ alignSelf: 'flex-start' }}
              >
                {t('pick_existing_part')}
              </Link>
            </>
          )}

          <Divider />

          <Stack direction="row" spacing={2}>
            <TextField
              label={t('position')}
              value={positionCode}
              onChange={(event) => setPositionCode(event.target.value)}
              placeholder="LUBE"
              fullWidth
            />
            <TextField
              label={t('qty_per_assembly')}
              value={qtyPerAssembly}
              onChange={(event) => {
                setQtyPerAssembly(event.target.value);
                setError(null);
              }}
              type="number"
              sx={{ width: 160 }}
            />
          </Stack>

          <FormControlLabel
            control={
              <Switch
                checked={consumable}
                onChange={(event) => setConsumable(event.target.checked)}
              />
            }
            label={t('consumable')}
          />

          <Stack direction="row" spacing={2}>
            <TextField
              label={t('replace_interval_hours')}
              value={intervalHours}
              onChange={(event) => setIntervalHours(event.target.value)}
              type="number"
              fullWidth
            />
            <TextField
              label={t('replace_interval_months')}
              value={intervalMonths}
              onChange={(event) => setIntervalMonths(event.target.value)}
              type="number"
              fullWidth
            />
          </Stack>

          <TextField
            label={t('notes')}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            fullWidth
            multiline
            rows={2}
          />

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('cancel')}
        </Button>
        <Button variant="contained" onClick={submit} disabled={saving}>
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BomLineFormDialog;
