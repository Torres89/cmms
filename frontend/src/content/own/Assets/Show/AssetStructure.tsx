import { FC, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Collapse,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Tooltip,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import ExpandMoreTwoToneIcon from '@mui/icons-material/ExpandMoreTwoTone';
import ChevronRightTwoToneIcon from '@mui/icons-material/ChevronRightTwoTone';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import PrecisionManufacturingTwoToneIcon from '@mui/icons-material/PrecisionManufacturingTwoTone';
import MoreVertTwoToneIcon from '@mui/icons-material/MoreVertTwoTone';
import { useNavigate } from 'react-router-dom';
import {
  ComponentSummary,
  StructureNode
} from '../../../../models/owns/dossier';
import { AssetDTO } from '../../../../models/owns/asset';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetStructure from './structure/useAssetStructure';
import PositionFormDialog from './structure/PositionFormDialog';
import InstallComponentDialog from './structure/InstallComponentDialog';
import ComponentActionDialog, {
  ComponentActionKind
} from './structure/ComponentActionDialog';

interface PropsType {
  asset: AssetDTO;
  canEdit?: boolean;
}

/**
 * The "airplane" view: the equipment breakdown structure, with the serialized
 * components currently occupying each position badged by remaining life.
 *
 * A position outlives whatever is installed in it, which is exactly why the
 * tree shows positions and the badges show components — and why editing a
 * position and moving a component are two different operations rather than one
 * form.
 */
const AssetStructure: FC<PropsType> = ({ asset, canEdit = false }) => {
  const { t }: { t: any } = useTranslation();
  const {
    dossier,
    spares,
    loading,
    addPosition,
    updatePosition,
    deletePosition,
    install,
    createAndInstall,
    removeComponent,
    overhaul,
    scrap,
    deleteComponent
  } = useAssetStructure(asset);

  const [positionDialog, setPositionDialog] = useState<{
    node: StructureNode | null;
    parentId: number;
    parentName?: string;
  } | null>(null);
  const [installInto, setInstallInto] = useState<StructureNode | null>(null);
  const [componentAction, setComponentAction] = useState<{
    kind: ComponentActionKind;
    component: ComponentSummary;
  } | null>(null);
  const [pendingPositionDelete, setPendingPositionDelete] =
    useState<StructureNode | null>(null);
  const [pendingComponentDelete, setPendingComponentDelete] =
    useState<ComponentSummary | null>(null);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }
  if (!dossier) return null;

  const submitPosition = (payload) => {
    if (!positionDialog) return Promise.reject();
    return positionDialog.node
      ? updatePosition(positionDialog.node.id, payload)
      : addPosition(positionDialog.parentId, payload);
  };

  const actionHandler = (kind: ComponentActionKind) =>
    kind === 'remove' ? removeComponent : kind === 'overhaul' ? overhaul : scrap;

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('equipment_breakdown')}</Typography>
        {canEdit && (
          <Button
            variant="contained"
            startIcon={<AddTwoToneIcon />}
            onClick={() =>
              setPositionDialog({
                node: null,
                parentId: asset.id,
                parentName: asset.name
              })
            }
          >
            {t('add_position')}
          </Button>
        )}
      </Stack>

      {!dossier.structure.length ? (
        <Alert severity="info">
          {t('no_structure_yet')}{' '}
          <strong>{t('instantiate_a_pack_to_build_it')}</strong>
        </Alert>
      ) : (
        <Card sx={{ p: 2 }}>
          {dossier.structure.map((node) => (
            <StructureRow
              key={node.id}
              node={node}
              depth={0}
              components={dossier.components}
              canEdit={canEdit}
              onAddChild={(parent) =>
                setPositionDialog({
                  node: null,
                  parentId: parent.id,
                  parentName: parent.name
                })
              }
              onEdit={(target) =>
                setPositionDialog({
                  node: target,
                  parentId: target.id,
                  parentName: undefined
                })
              }
              onDelete={setPendingPositionDelete}
              onInstall={setInstallInto}
              onComponentAction={(kind, component) =>
                setComponentAction({ kind, component })
              }
              onComponentDelete={setPendingComponentDelete}
            />
          ))}
        </Card>
      )}

      <PositionFormDialog
        open={!!positionDialog}
        onClose={() => setPositionDialog(null)}
        node={positionDialog?.node ?? null}
        parentName={positionDialog?.parentName}
        onSubmit={submitPosition}
      />

      <InstallComponentDialog
        open={!!installInto}
        onClose={() => setInstallInto(null)}
        position={installInto}
        spares={spares}
        onInstallExisting={install}
        onCreateAndInstall={createAndInstall}
      />

      <ComponentActionDialog
        open={!!componentAction}
        onClose={() => setComponentAction(null)}
        kind={componentAction?.kind ?? 'remove'}
        component={componentAction?.component ?? null}
        onSubmit={actionHandler(componentAction?.kind ?? 'remove')}
      />

      <ConfirmDialog
        open={!!pendingPositionDelete}
        onCancel={() => setPendingPositionDelete(null)}
        onConfirm={() => {
          const target = pendingPositionDelete;
          setPendingPositionDelete(null);
          if (target) deletePosition(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_position')}
      />

      <ConfirmDialog
        open={!!pendingComponentDelete}
        onCancel={() => setPendingComponentDelete(null)}
        onConfirm={() => {
          const target = pendingComponentDelete;
          setPendingComponentDelete(null);
          if (target) deleteComponent(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_component')}
      />
    </Stack>
  );
};

interface RowProps {
  node: StructureNode;
  depth: number;
  components: ComponentSummary[];
  canEdit: boolean;
  onAddChild: (node: StructureNode) => void;
  onEdit: (node: StructureNode) => void;
  onDelete: (node: StructureNode) => void;
  onInstall: (node: StructureNode) => void;
  onComponentAction: (
    kind: ComponentActionKind,
    component: ComponentSummary
  ) => void;
  onComponentDelete: (component: ComponentSummary) => void;
}

const StructureRow: FC<RowProps> = ({
  node,
  depth,
  components,
  canEdit,
  onAddChild,
  onEdit,
  onDelete,
  onInstall,
  onComponentAction,
  onComponentDelete
}) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();
  const navigate = useNavigate();
  const [open, setOpen] = useState(depth < 1);

  // Matched on id, not name: two positions can share a name, and renaming one
  // must not move whatever is fitted in it.
  const installed = components.filter(
    (component) => component.positionId === node.id
  );
  const hasChildren = node.children.length > 0;

  return (
    <Box>
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{
          pl: depth * 3,
          py: 0.75,
          borderBottom: `1px solid ${theme.colors.alpha.black[5]}`,
          '&:hover .structure-row-actions': { opacity: 1 }
        }}
      >
        <IconButton
          size="small"
          onClick={() => setOpen(!open)}
          sx={{ visibility: hasChildren ? 'visible' : 'hidden' }}
        >
          {open ? (
            <ExpandMoreTwoToneIcon fontSize="small" />
          ) : (
            <ChevronRightTwoToneIcon fontSize="small" />
          )}
        </IconButton>

        {node.positionCode && (
          <Chip
            size="small"
            label={node.positionCode}
            sx={{ fontFamily: 'monospace', fontSize: 11, height: 20 }}
          />
        )}

        <Typography
          variant="body2"
          sx={{ cursor: 'pointer', fontWeight: depth === 0 ? 600 : 400 }}
          onClick={() => navigate(`/app/assets/${node.id}/details`)}
        >
          {node.name}
        </Typography>

        {node.criticality != null && node.criticality >= 4 && (
          <Tooltip title={t('critical_position')}>
            <Chip
              size="small"
              color="warning"
              variant="outlined"
              label="!"
              sx={{ height: 18 }}
            />
          </Tooltip>
        )}

        {node.trackingClass && node.trackingClass !== 'NON_TRACKED' && (
          <Chip
            size="small"
            variant="outlined"
            label={t(node.trackingClass)}
            sx={{ height: 18, fontSize: 10 }}
          />
        )}

        {installed.map((component) => (
          <ComponentBadge
            key={component.id}
            component={component}
            canEdit={canEdit}
            onAction={onComponentAction}
            onDelete={onComponentDelete}
          />
        ))}

        <Box sx={{ flex: 1 }} />

        {canEdit && (
          <Stack
            direction="row"
            className="structure-row-actions"
            sx={{ opacity: { xs: 1, md: 0 }, transition: 'opacity 150ms' }}
          >
            <Tooltip title={t('install_component')}>
              <IconButton size="small" onClick={() => onInstall(node)}>
                <PrecisionManufacturingTwoToneIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('add_position')}>
              <IconButton size="small" onClick={() => onAddChild(node)}>
                <AddTwoToneIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('edit_position')}>
              <IconButton size="small" onClick={() => onEdit(node)}>
                <EditTwoToneIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('to_delete')}>
              <IconButton size="small" onClick={() => onDelete(node)}>
                <DeleteTwoToneIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          </Stack>
        )}
      </Stack>

      {hasChildren && (
        <Collapse in={open}>
          {node.children.map((child) => (
            <StructureRow
              key={child.id}
              node={child}
              depth={depth + 1}
              components={components}
              canEdit={canEdit}
              onAddChild={onAddChild}
              onEdit={onEdit}
              onDelete={onDelete}
              onInstall={onInstall}
              onComponentAction={onComponentAction}
              onComponentDelete={onComponentDelete}
            />
          ))}
        </Collapse>
      )}
    </Box>
  );
};

interface BadgeProps {
  component: ComponentSummary;
  canEdit: boolean;
  onAction: (kind: ComponentActionKind, component: ComponentSummary) => void;
  onDelete: (component: ComponentSummary) => void;
}

const ComponentBadge: FC<BadgeProps> = ({
  component,
  canEdit,
  onAction,
  onDelete
}) => {
  const { t }: { t: any } = useTranslation();
  const [anchor, setAnchor] = useState<null | HTMLElement>(null);
  const remaining = component.remainingLifeFraction;

  // The aviation convention: warn at 10 % remaining, again at 5 %.
  const colour =
    remaining == null
      ? 'default'
      : remaining <= 0.05
      ? 'error'
      : remaining <= 0.1
      ? 'warning'
      : 'success';

  const label =
    remaining == null
      ? `SN ${component.serialNumber}`
      : `SN ${component.serialNumber} — ${Math.round(remaining * 100)}%`;

  return (
    <>
      <Tooltip
        title={
          <Box>
            <div>{component.name ?? t('component')}</div>
            {component.totalHours != null && (
              <div>
                {Math.round(component.totalHours).toLocaleString()} h
                {component.hourLimit != null &&
                  ` / ${component.hourLimit.toLocaleString()} h`}
              </div>
            )}
            {component.installedAt && (
              <div>
                {t('installed')}{' '}
                {new Date(component.installedAt).toLocaleDateString()}
              </div>
            )}
          </Box>
        }
      >
        <Chip
          size="small"
          color={colour as any}
          label={label}
          sx={{ height: 20, fontSize: 11 }}
          onDelete={canEdit ? (event) => setAnchor(event.currentTarget) : undefined}
          deleteIcon={<MoreVertTwoToneIcon />}
        />
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={!!anchor}
        onClose={() => setAnchor(null)}
      >
        {(['remove', 'overhaul', 'scrap'] as ComponentActionKind[]).map(
          (kind) => (
            <MenuItem
              key={kind}
              onClick={() => {
                setAnchor(null);
                onAction(kind, component);
              }}
            >
              {t(kind === 'remove' ? 'remove_component' : kind)}
            </MenuItem>
          )
        )}
        <MenuItem
          onClick={() => {
            setAnchor(null);
            onDelete(component);
          }}
          sx={{ color: 'error.main' }}
        >
          {t('to_delete')}
        </MenuItem>
      </Menu>
    </>
  );
};

export default AssetStructure;
