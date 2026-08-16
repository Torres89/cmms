import { FC, useState } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import ShoppingCartTwoToneIcon from '@mui/icons-material/ShoppingCartTwoTone';
import OpenInNewTwoToneIcon from '@mui/icons-material/OpenInNewTwoTone';
import BuildTwoToneIcon from '@mui/icons-material/BuildTwoTone';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import { useNavigate } from 'react-router-dom';
import { BomLine } from '../../../../models/owns/dossier';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetBom from './bom/useAssetBom';
import BomLineFormDialog from './bom/BomLineFormDialog';

interface PropsType {
  assetId: number;
  canEdit?: boolean;
}

/**
 * The bill of materials: the single record of what parts this machine takes.
 *
 * It replaced a second, unrelated "Parts" tab that held the same idea in a
 * different table and never agreed with this one. Writing a line here now also
 * links the part to the machine, so Inventory can answer "what needs this?".
 *
 * The inventory column is the point of the tab for a planner: knowing a machine
 * takes a filter is worth much less than knowing whether there is one on the
 * shelf.
 */
const AssetBom: FC<PropsType> = ({ assetId, canEdit = false }) => {
  const { t }: { t: any } = useTranslation();
  const navigate = useNavigate();
  const {
    lines,
    note,
    kit,
    loading,
    create,
    update,
    remove,
    createPart,
    loadKit
  } = useAssetBom(assetId);

  const [editing, setEditing] = useState<{ line: BomLine | null } | null>(null);
  const [pendingDelete, setPendingDelete] = useState<BomLine | null>(null);
  const [loadingKit, setLoadingKit] = useState(false);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  const handleLoadKit = () => {
    setLoadingKit(true);
    loadKit().finally(() => setLoadingKit(false));
  };

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('bill_of_materials')}</Typography>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<ShoppingCartTwoToneIcon />}
            onClick={handleLoadKit}
            disabled={loadingKit || !lines.length}
          >
            {t('order_what_is_due')}
          </Button>
          {canEdit && (
            <Button
              variant="contained"
              startIcon={<AddTwoToneIcon />}
              onClick={() => setEditing({ line: null })}
            >
              {t('add_bom_line')}
            </Button>
          )}
        </Stack>
      </Stack>

      {kit && (
        <Card sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 0.5 }}>
            {t('restock_kit')} — {t('next')} {kit.horizonDays} {t('days')}
          </Typography>
          {kit.hoursPerDay > 0 && (
            <Typography variant="caption" color="text.secondary">
              {t('based_on_measured_usage')}: {kit.hoursPerDay.toFixed(1)} h/
              {t('day')}
            </Typography>
          )}
          <Divider sx={{ my: 1.5 }} />
          {kit.note && <Alert severity="info">{kit.note}</Alert>}
          {kit.lines.map((line) => (
            <Stack
              key={line.partId}
              direction="row"
              spacing={1.5}
              alignItems="center"
              sx={{ py: 0.75 }}
            >
              {line.urgent && (
                <Tooltip title={t('lead_time_exceeds_time_remaining')}>
                  <Chip
                    size="small"
                    color="error"
                    label={t('order_now')}
                    sx={{ height: 20 }}
                  />
                </Tooltip>
              )}
              <Typography variant="body2" sx={{ flex: 1 }}>
                {line.name}
                {line.mpn && (
                  <Typography
                    component="span"
                    variant="caption"
                    color="text.secondary"
                  >
                    {' '}
                    · {line.mpn}
                  </Typography>
                )}
              </Typography>
              <Typography variant="body2">
                {line.shortfall} {line.unit ?? ''}
              </Typography>
              {line.daysUntilDue != null && (
                <Typography variant="caption" color="text.secondary">
                  {t('due_in')} {line.daysUntilDue} {t('days')}
                </Typography>
              )}
              {line.unitPrice != null && (
                <Typography variant="body2">
                  {(line.unitPrice * line.shortfall).toFixed(2)}{' '}
                  {line.currency ?? ''}
                </Typography>
              )}
              {line.productUrl && (
                <Link
                  href={line.productUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <OpenInNewTwoToneIcon fontSize="small" />
                </Link>
              )}
            </Stack>
          ))}
          {kit.estimatedTotal > 0 && (
            <>
              <Divider sx={{ my: 1 }} />
              <Typography variant="subtitle2" align="right">
                {t('estimated_total')}: {kit.estimatedTotal.toFixed(2)}
              </Typography>
            </>
          )}
        </Card>
      )}

      {!lines.length ? (
        <Alert severity="info" icon={<BuildTwoToneIcon />}>
          {note ?? t('no_bom_captured')}
        </Alert>
      ) : (
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell />
                <TableCell>{t('part')}</TableCell>
                <TableCell>{t('position')}</TableCell>
                <TableCell align="right">{t('qty_per_assembly')}</TableCell>
                <TableCell>{t('availability')}</TableCell>
                <TableCell>{t('replacement_interval')}</TableCell>
                {canEdit && <TableCell align="right" />}
              </TableRow>
            </TableHead>
            <TableBody>
              {lines.map((line) => (
                <TableRow
                  key={line.id}
                  hover
                  sx={{ cursor: line.part ? 'pointer' : 'default' }}
                  onClick={() =>
                    line.part && navigate(`/app/inventory/parts/${line.part.id}`)
                  }
                >
                  <TableCell sx={{ width: 56 }}>
                    <Avatar
                      variant="rounded"
                      src={line.part?.image?.url}
                      sx={{ width: 40, height: 40 }}
                    >
                      <BuildTwoToneIcon fontSize="small" />
                    </Avatar>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{line.part?.name}</Typography>
                    {line.part?.mpn && (
                      <Typography variant="caption" color="text.secondary">
                        {line.part.manufacturer
                          ? `${line.part.manufacturer} · `
                          : ''}
                        {line.part.mpn}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {line.positionCode && (
                      <Chip
                        size="small"
                        label={line.positionCode}
                        sx={{
                          fontFamily: 'monospace',
                          fontSize: 11,
                          height: 20
                        }}
                      />
                    )}
                    {line.consumable && (
                      <Chip
                        size="small"
                        color="info"
                        variant="outlined"
                        label={t('consumable')}
                        sx={{ ml: 0.5, height: 20, fontSize: 10 }}
                      />
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {line.qtyPerAssembly ?? 1}
                  </TableCell>
                  <TableCell>
                    <AvailabilityChip line={line} />
                  </TableCell>
                  <TableCell>
                    {line.replaceIntervalHours
                      ? `${line.replaceIntervalHours.toLocaleString()} h`
                      : line.replaceIntervalMonths
                      ? `${line.replaceIntervalMonths} ${t('months')}`
                      : '—'}
                  </TableCell>
                  {canEdit && (
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title={t('edit')}>
                        <IconButton
                          size="small"
                          onClick={(event) => {
                            event.stopPropagation();
                            setEditing({ line });
                          }}
                        >
                          <EditTwoToneIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t('to_delete')}>
                        <IconButton
                          size="small"
                          onClick={(event) => {
                            event.stopPropagation();
                            setPendingDelete(line);
                          }}
                        >
                          <DeleteTwoToneIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <BomLineFormDialog
        open={!!editing}
        onClose={() => setEditing(null)}
        line={editing?.line ?? null}
        onSubmit={(payload) =>
          editing?.line ? update(editing.line.id, payload) : create(payload)
        }
        onCreatePart={createPart}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          setPendingDelete(null);
          if (target) remove(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_bom_line')}
      />
    </Stack>
  );
};

/**
 * Whether the shelf can actually supply this line.
 *
 * "Not stocked" is deliberately neutral rather than a warning: an
 * order-on-demand part sitting at zero is the intended state, and colouring it
 * red trains people to ignore the column.
 */
const AvailabilityChip: FC<{ line: BomLine }> = ({ line }) => {
  const { t }: { t: any } = useTranslation();
  const onHand = line.part?.quantity ?? 0;
  const needed = line.qtyPerAssembly ?? 1;
  const unit = line.part?.unit ? ` ${line.part.unit}` : '';

  if (line.part?.nonStock) {
    return (
      <Chip
        size="small"
        variant="outlined"
        label={t('not_stocked')}
        sx={{ height: 20, fontSize: 11 }}
      />
    );
  }
  if (onHand <= 0) {
    return (
      <Chip
        size="small"
        color="error"
        variant="outlined"
        label={t('out_of_stock')}
        sx={{ height: 20, fontSize: 11 }}
      />
    );
  }
  if (onHand < needed) {
    return (
      <Tooltip title={`${onHand}${unit} / ${needed}${unit}`}>
        <Chip
          size="small"
          color="warning"
          variant="outlined"
          label={`${t('short_by')} · ${onHand}${unit}`}
          sx={{ height: 20, fontSize: 11 }}
        />
      </Tooltip>
    );
  }
  return (
    <Chip
      size="small"
      color="success"
      variant="outlined"
      label={`${t('in_stock')} · ${onHand}${unit}`}
      sx={{ height: 20, fontSize: 11 }}
    />
  );
};

export default AssetBom;
