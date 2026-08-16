import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import MultipleTabsLayout from '../../components/MultipleTabsLayout';
import { TitleContext } from '../../../../contexts/TitleContext';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import Asset, {
  AssetDTO,
  assetLevels,
  trackingClasses
} from '../../../../models/owns/asset';
import AssetWorkOrders from './AssetWorkOrders';
import AssetDetails from './AssetDetails';
import { isNumeric } from 'src/utils/validators';
import { IField } from '../../type';
import {
  Box,
  Dialog,
  DialogContent,
  DialogTitle,
  Typography
} from '@mui/material';
import Form from '../../components/form';
import * as Yup from 'yup';
import {
  deleteAsset,
  editAsset,
  getAssetDetails
} from '../../../../slices/asset';
import { useDispatch, useSelector } from '../../../../store';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { formatAssetValues } from '../../../../utils/formatters';
import { CompanySettingsContext } from '../../../../contexts/CompanySettingsContext';
import { PermissionEntity } from '../../../../models/owns/role';
import PermissionErrorMessage from '../../components/PermissionErrorMessage';
import useAuth from '../../../../hooks/useAuth';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import ConfirmDialog from '../../components/ConfirmDialog';
import AssetMeters from './AssetMeters';
import { getImageAndFiles } from '../../../../utils/overall';
import AssetDowntimes from './AssetDowntimes';
import AssetAnalytics from './AssetAnalytics';
import AssetDossierHeader from './AssetDossierHeader';
import AssetStructure from './AssetStructure';
import AssetSpecs from './AssetSpecs';
import AssetBom from './AssetBom';
import AssetDocuments from './AssetDocuments';
import AssetTimeline from './AssetTimeline';
import apiUtil from '../../../../utils/api';
import { AssetDossier } from '../../../../models/owns/dossier';

interface PropsType {}

const ShowAsset = ({}: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const { assetId } = useParams();
  const [openUpdateModal, setOpenUpdateModal] = useState<boolean>(false);
  const { setTitle } = useContext(TitleContext);
  const { uploadFiles } = useContext(CompanySettingsContext);
  const location = useLocation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { assetInfos, loadingGet } = useSelector((state) => state.assets);
  const asset: AssetDTO = assetInfos[assetId]?.asset;
  const navigate = useNavigate();
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const {
    hasViewPermission,
    hasEditPermission,
    hasDeletePermission,
    getFilteredFields
  } = useAuth();
  const dispatch = useDispatch();

  const [dossier, setDossier] = useState<AssetDossier | null>(null);

  useEffect(() => {
    if (isNumeric(assetId)) dispatch(getAssetDetails(Number(assetId)));
  }, [assetId]);

  useEffect(() => {
    if (!isNumeric(assetId)) return;
    // The dossier header sits above every tab, so it is fetched once here
    // rather than by each tab separately.
    apiUtil
      .get<AssetDossier>(`assets/${assetId}/dossier`)
      .then(setDossier)
      .catch(() => setDossier(null));
  }, [assetId]);

  const handleOpenUpdateModal = () => setOpenUpdateModal(true);
  const handleCloseUpdateModal = () => setOpenUpdateModal(false);

  const handleDelete = () => {
    dispatch(deleteAsset(asset.id))
      .then(onDeleteSuccess)
      .catch(onDeleteFailure);
  };
  useEffect(() => {
    setTitle(asset?.name);
  }, [asset]);

  const arr = location.pathname.split('/');

  // The dossier tabs come first because they answer the questions people
  // actually arrive with. The original tabs stay where they were so nobody's
  // muscle memory breaks.
  //
  // There is no separate "Parts" tab: it listed the asset-part association
  // table while Parts & BOM listed the bill of materials, and the two never
  // agreed. The BOM won because it carries quantity, position and replacement
  // interval, and it now writes the association too.
  const tabs = [
    { value: 'details', label: t('details') },
    { value: 'structure', label: t('structure') },
    { value: 'specs', label: t('specs') },
    { value: 'bom', label: t('parts_bom') },
    { value: 'documents', label: t('files_and_documents') },
    { value: 'history', label: t('history') },
    { value: 'work-orders', label: t('work_orders') },
    { value: 'meters', label: t('meters') },
    { value: 'downtimes', label: t('downtimes') },
    { value: 'analytics', label: t('analytics') }
  ];
  const tabIndex = tabs.findIndex((tab) => tab.value === arr[arr.length - 1]);

  // Keyed on the tab's value rather than its position. The chain of
  // `tabIndex === n` this replaced meant that removing or reordering a single
  // tab silently shifted every tab after it onto the wrong component.
  const canEditAsset = hasEditPermission(PermissionEntity.ASSETS, asset);
  const renderTab = (value?: string) => {
    switch (value) {
      case 'details':
        return <AssetDetails asset={asset} loading={loadingGet} />;
      case 'structure':
        return <AssetStructure asset={asset} canEdit={canEditAsset} />;
      case 'specs':
        return <AssetSpecs assetId={Number(assetId)} canEdit={canEditAsset} />;
      case 'bom':
        return <AssetBom assetId={Number(assetId)} canEdit={canEditAsset} />;
      case 'documents':
        return <AssetDocuments asset={asset} canEdit={canEditAsset} />;
      case 'history':
        return (
          <AssetTimeline
            assetId={Number(assetId)}
            equipmentClass={asset?.equipmentClass}
            canEdit={canEditAsset}
          />
        );
      case 'work-orders':
        return <AssetWorkOrders asset={asset} />;
      case 'meters':
        return <AssetMeters asset={asset} />;
      case 'downtimes':
        return <AssetDowntimes asset={asset} />;
      case 'analytics':
        return <AssetAnalytics id={Number(assetId)} />;
      default:
        return null;
    }
  };
  const onDeleteSuccess = () => {
    showSnackBar(t('asset_remove_success'), 'success');
    navigate('/app/assets');
  };
  const onDeleteFailure = (err) =>
    showSnackBar(t('asset_remove_failure'), 'error');
  const defaultFields: Array<IField> = [
    {
      name: 'assetInfo',
      type: 'titleGroupField',
      label: t('asset_information')
    },
    {
      name: 'name',
      type: 'text',
      label: t('name'),
      placeholder: t('asset_name_description'),
      required: true
    },
    {
      name: 'location',
      type: 'select',
      type2: 'location',
      label: t('location'),
      placeholder: t('select_asset_location'),
      required: true,
      midWidth: true
    },
    {
      name: 'acquisitionCost',
      type: 'number',
      label: t('acquisition_cost'),
      placeholder: t('acquisition_cost'),
      midWidth: true
    },
    {
      name: 'description',
      type: 'text',
      label: t('description'),
      placeholder: t('description'),
      multiple: true
    },
    {
      name: 'manufacturer',
      type: 'text',
      label: t('manufacturer'),
      placeholder: t('manufacturer'),
      midWidth: true
    },
    {
      name: 'power',
      type: 'text',
      label: t('power'),
      placeholder: t('power'),
      midWidth: true
    },
    {
      name: 'model',
      type: 'text',
      label: t('model'),
      placeholder: t('model'),
      midWidth: true
    },
    {
      name: 'barCode',
      type: 'text',
      label: t('barcode'),
      placeholder: t('barcode'),
      midWidth: true
    },
    {
      name: 'serialNumber',
      type: 'text',
      label: t('serial_number'),
      placeholder: t('serial_number'),
      midWidth: true
    },
    {
      name: 'category',
      midWidth: true,
      label: t('category'),
      placeholder: t('category'),
      type: 'select',
      type2: 'category',
      category: 'asset-categories'
    },
    {
      name: 'area',
      type: 'text',
      midWidth: true,
      label: t('area'),
      placeholder: t('area')
    },
    {
      name: 'image',
      type: 'file',
      fileType: 'image',
      label: t('image')
    },
    {
      // Not 'assignedTo': that is the name of the select below, and two fields
      // sharing a name make React drop one of them from the grid.
      name: 'assignedToGroup',
      type: 'titleGroupField',
      label: t('assigned_to')
    },
    {
      name: 'primaryUser',
      type: 'select',
      type2: 'user',
      label: t('worker'),
      placeholder: t('primary_user_description')
    },
    {
      name: 'assignedTo',
      type: 'select',
      type2: 'user',
      multiple: true,
      label: t('additional_workers'),
      placeholder: 'Select additional workers'
    },
    {
      name: 'teams',
      type: 'select',
      type2: 'team',
      multiple: true,
      label: t('teams'),
      placeholder: t('teams_description')
    },
    // The machine profile. These columns drive the dossier header, the
    // structure tree, the spec-key catalog and the failure-mode catalog, and
    // until now there was nowhere in the app to set any of them.
    {
      name: 'machineProfile',
      type: 'titleGroupField',
      label: t('machine_profile')
    },
    {
      name: 'equipmentClass',
      type: 'text',
      midWidth: true,
      label: t('equipment_class'),
      placeholder: 'CNC_MACHINING_CENTER_VMC',
      helperText: t('equipment_class_description')
    },
    {
      name: 'level',
      type: 'select',
      midWidth: true,
      label: t('asset_level'),
      items: assetLevels.map((level) => ({ label: t(level), value: level }))
    },
    {
      name: 'positionCode',
      type: 'text',
      midWidth: true,
      label: t('position_code'),
      placeholder: 'SPN'
    },
    {
      name: 'trackingClass',
      type: 'select',
      midWidth: true,
      label: t('tracking_class'),
      items: trackingClasses.map((tracking) => ({
        label: t(tracking),
        value: tracking
      }))
    },
    {
      name: 'criticality',
      type: 'number',
      midWidth: true,
      label: t('criticality'),
      placeholder: '1 - 5'
    },
    {
      name: 'downtimeCostPerHour',
      type: 'number',
      midWidth: true,
      label: t('downtime_cost_per_hour')
    },
    {
      name: 'replacementCost',
      type: 'number',
      midWidth: true,
      label: t('replacement_cost')
    },
    {
      name: 'functionalDescription',
      type: 'text',
      multiple: true,
      label: t('functional_description'),
      placeholder: t('functional_description_description')
    },
    {
      name: 'moreInfos',
      type: 'titleGroupField',
      label: t('more_informations')
    },
    {
      name: 'customers',
      type: 'select',
      type2: 'customer',
      multiple: true,
      label: t('customers'),
      placeholder: t('customers_description')
    },
    {
      name: 'vendors',
      type: 'select',
      type2: 'vendor',
      multiple: true,
      label: t('vendors'),
      placeholder: t('vendors_description')
    },
    {
      name: 'inServiceDate',
      type: 'date',
      midWidth: true,
      label: t('inServiceDate_description')
    },
    {
      name: 'warrantyExpirationDate',
      type: 'date',
      midWidth: true,
      label: t('warranty_expiration_date')
    },
    {
      name: 'additionalInfos',
      type: 'text',
      label: t('additional_information'),
      placeholder: t('additional_information'),
      multiple: true
    },
    {
      name: 'files',
      type: 'file',
      multiple: true,
      label: t('files'),
      fileType: 'file'
    },
    {
      name: 'structure',
      type: 'titleGroupField',
      label: t('structure')
    },
    { name: 'parts', type: 'select', type2: 'part', label: t('parts') },
    {
      name: 'parentAsset',
      type: 'select',
      type2: 'asset',
      label: t('parent_asset'),
      excluded: Number(assetId)
    }
  ];

  const shape = {
    name: Yup.string().required(t('required_asset_name'))
  };
  const onEditSuccess = () => {
    setOpenUpdateModal(false);
    showSnackBar(t('changes_saved_success'), 'success');
  };
  const onEditFailure = (err) =>
    showSnackBar(t('asset_update_failure'), 'error');

  const renderAssetUpdateModal = () => (
    <Dialog
      fullWidth
      maxWidth="md"
      open={openUpdateModal}
      onClose={handleCloseUpdateModal}
    >
      <DialogTitle
        sx={{
          p: 3
        }}
      >
        <Typography variant="h4" gutterBottom>
          {t('edit_asset')}
        </Typography>
        <Typography variant="subtitle2">
          {t('edit_asset_description')}
        </Typography>
      </DialogTitle>
      <DialogContent
        dividers
        sx={{
          p: 3
        }}
      >
        <Box>
          <Form
            fields={getFilteredFields(defaultFields)}
            validation={Yup.object().shape(shape)}
            submitText={t('save')}
            values={{
              ...asset,
              location: asset?.location
                ? {
                    label: asset?.location.name,
                    value: asset?.location.id
                  }
                : null,
              category: asset?.category
                ? {
                    label: asset.category.name,
                    value: asset.category.id
                  }
                : null,
              primaryUser: asset?.primaryUser
                ? {
                    label: `${asset?.primaryUser.firstName} ${asset?.primaryUser.lastName}`,
                    value: asset?.primaryUser.id
                  }
                : null,
              assignedTo: asset?.assignedTo?.map((user) => {
                return {
                  label: `${user.firstName} ${user.lastName}`,
                  value: user.id
                };
              }),
              customers: asset?.customers?.map((customer) => {
                return {
                  label: customer.name,
                  value: customer.id
                };
              }),
              vendors: asset?.vendors?.map((vendor) => {
                return {
                  label: vendor.companyName,
                  value: vendor.id
                };
              }),
              teams: asset?.teams?.map((team) => {
                return {
                  label: team.name,
                  value: team.id
                };
              }),
              parts:
                asset?.parts?.map((part) => {
                  return {
                    label: part.name,
                    value: part.id
                  };
                }) ?? [],
              parentAsset: asset?.parentAsset
                ? {
                    label: asset.parentAsset.name,
                    value: asset.parentAsset.id
                  }
                : null,
              level: asset?.level
                ? { label: t(asset.level), value: asset.level }
                : null,
              trackingClass: asset?.trackingClass
                ? { label: t(asset.trackingClass), value: asset.trackingClass }
                : null
            }}
            onChange={({ field, e }) => {}}
            onSubmit={async (values) => {
              let formattedValues = formatAssetValues(values);
              const files = formattedValues.files.find((file) => file.id)
                ? []
                : formattedValues.files;
              return new Promise<void>((resolve, rej) => {
                uploadFiles(files, formattedValues.image)
                  .then((files) => {
                    const imageAndFiles = getImageAndFiles(files, asset.image);
                    formattedValues = {
                      ...formattedValues,
                      image: imageAndFiles.image,
                      files: [...asset.files, ...imageAndFiles.files]
                    };
                    dispatch(editAsset(Number(assetId), formattedValues))
                      .then(onEditSuccess)
                      .catch(onEditFailure)
                      .finally(resolve);
                  })
                  .catch((err) => {
                    onEditFailure(err);
                    rej(err);
                  });
              });
            }}
          />
        </Box>
      </DialogContent>
    </Dialog>
  );
  if (hasViewPermission(PermissionEntity.ASSETS))
    return (
      <MultipleTabsLayout
        basePath={`/app/assets/${assetId}`}
        tabs={tabs}
        tabIndex={tabIndex}
        title={`Asset`}
        action={
          hasEditPermission(PermissionEntity.ASSETS, asset)
            ? handleOpenUpdateModal
            : null
        }
        actionTitle={t('edit')}
        secondAction={() => {
          setOpenDelete(true);
        }}
        secondActionTitle={
          hasDeletePermission(PermissionEntity.ASSETS, asset)
            ? t('to_delete')
            : null
        }
        secondActionIcon={<DeleteTwoToneIcon />}
        withoutCard
        editAction
      >
        {isNumeric(assetId) ? (
          <>
            {dossier && <AssetDossierHeader dossier={dossier} />}
            {renderTab(tabs[tabIndex]?.value)}
          </>
        ) : null}
        <ConfirmDialog
          open={openDelete}
          onCancel={() => {
            setOpenDelete(false);
          }}
          onConfirm={handleDelete}
          confirmText={t('to_delete')}
          question={t('confirm_delete_asset')}
        />
        {renderAssetUpdateModal()}
      </MultipleTabsLayout>
    );
  else return <PermissionErrorMessage message={'no_access_assets'} />;
};

export default ShowAsset;
