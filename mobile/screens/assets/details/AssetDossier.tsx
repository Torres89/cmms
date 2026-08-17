import { useEffect, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import {
  ActivityIndicator,
  Badge,
  Button,
  Card,
  Chip,
  Divider,
  ProgressBar,
  Text,
  useTheme
} from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import AsyncStorage from '@react-native-async-storage/async-storage';
import api from '../../../utils/api';

/**
 * The technician's landing view: what is true about this machine right now, and
 * the four things they are standing there to do.
 *
 * Scan-first is the whole design. The technician is already at the machine with
 * a phone, so the flow is scan -> this screen -> one tap. The dossier is cached
 * so a shop floor with no signal still shows the last known state rather than a
 * spinner.
 */
export default function AssetDossier({ asset, navigation }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const [dossier, setDossier] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [stale, setStale] = useState(false);

  const cacheKey = `dossier:${asset?.id}`;

  const load = async (isRefresh = false) => {
    if (!asset?.id) return;
    if (isRefresh) setRefreshing(true);
    try {
      const fresh = await api.get<any>(`assets/${asset.id}/dossier`);
      setDossier(fresh);
      setStale(false);
      await AsyncStorage.setItem(cacheKey, JSON.stringify(fresh));
    } catch (error) {
      // No signal in the shop is normal. Last known state beats a spinner.
      const cached = await AsyncStorage.getItem(cacheKey);
      if (cached) {
        setDossier(JSON.parse(cached));
        setStale(true);
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    load();
  }, [asset?.id]);

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator />
      </View>
    );
  }
  if (!dossier) {
    return (
      <View style={styles.centered}>
        <Text>{t('could_not_load_dossier')}</Text>
      </View>
    );
  }

  const primaryMeter = (dossier.meters ?? []).find((meter) => meter.lastValue != null);
  const dueSoon = (dossier.upcomingMaintenance ?? []).filter(
    (pm) => pm.due || pm.warning
  );
  const lowLife = (dossier.components ?? []).filter(
    (component) =>
      component.remainingLifeFraction != null &&
      component.remainingLifeFraction <= 0.15
  );

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.colors.background }}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} />
      }
    >
      {stale && (
        <Chip icon="wifi-off" style={styles.staleChip}>
          {t('showing_last_known_state')}
        </Chip>
      )}

      {/* The four things a technician standing at a machine actually does. */}
      <View style={styles.actionRow}>
        <Button
          mode="contained"
          icon="robot"
          style={styles.action}
          onPress={() =>
            navigation.navigate('AssetChat', {
              assetId: dossier.id,
              assetName: dossier.name
            })
          }
        >
          {t('ask')}
        </Button>
        <Button
          mode="contained-tonal"
          icon="gauge"
          style={styles.action}
          disabled={!primaryMeter}
          onPress={() =>
            primaryMeter &&
            navigation.push('MeterDetails', { id: primaryMeter.meterId })
          }
        >
          {t('log_reading')}
        </Button>
      </View>
      <View style={styles.actionRow}>
        <Button
          mode="contained-tonal"
          icon="alert"
          style={styles.action}
          onPress={() => navigation.push('AddRequest')}
        >
          {t('report_problem')}
        </Button>
        <Button
          mode="contained-tonal"
          icon="wrench"
          style={styles.action}
          onPress={() => navigation.push('AddWorkOrder', { asset })}
        >
          {t('start_work')}
        </Button>
      </View>

      <Card style={styles.card}>
        <Card.Content>
          <Text variant="titleMedium">{dossier.name}</Text>
          <Text variant="bodySmall" style={{ opacity: 0.7 }}>
            {[dossier.manufacturer, dossier.model, dossier.serialNumber && `SN ${dossier.serialNumber}`]
              .filter(Boolean)
              .join(' · ')}
          </Text>
          {dossier.locationPath ? (
            <Text variant="bodySmall" style={{ opacity: 0.7 }}>
              {dossier.locationPath}
            </Text>
          ) : null}
          <View style={styles.chipRow}>
            {dossier.status ? (
              <Chip compact style={styles.chip}>
                {t(dossier.status)}
              </Chip>
            ) : null}
            {dossier.criticality != null ? (
              <Chip compact style={styles.chip}>
                {t('criticality')} {dossier.criticality}/5
              </Chip>
            ) : null}
            {primaryMeter ? (
              <Chip compact style={styles.chip}>
                {Math.round(primaryMeter.lastValue).toLocaleString()}{' '}
                {primaryMeter.unit ?? ''}
              </Chip>
            ) : null}
          </View>
        </Card.Content>
      </Card>

      {dueSoon.length > 0 && (
        <Card style={styles.card}>
          <Card.Title title={t('maintenance_due')} />
          <Card.Content>
            {dueSoon.slice(0, 4).map((pm) => (
              <View key={pm.id} style={styles.row}>
                <Text style={{ flex: 1 }}>{pm.title}</Text>
                <Text variant="bodySmall">
                  {pm.remaining != null
                    ? `~${Math.round(pm.remaining)} ${pm.remainingUnit ?? ''}`
                    : t('due')}
                </Text>
              </View>
            ))}
          </Card.Content>
        </Card>
      )}

      {lowLife.length > 0 && (
        <Card style={styles.card}>
          <Card.Title title={t('components_near_limit')} />
          <Card.Content>
            {lowLife.map((component) => (
              <View key={component.id} style={styles.row}>
                <Text style={{ flex: 1 }}>
                  {component.positionCode ? `${component.positionCode} · ` : ''}
                  {component.name ?? component.serialNumber}
                </Text>
                <Text variant="bodySmall">
                  {Math.round(component.remainingLifeFraction * 100)}%
                </Text>
              </View>
            ))}
          </Card.Content>
        </Card>
      )}

      {(dossier.keySpecs ?? []).length > 0 && (
        <Card style={styles.card}>
          <Card.Title title={t('key_specs')} />
          <Card.Content>
            {dossier.keySpecs.slice(0, 10).map((spec) => (
              <View key={spec.id} style={styles.row}>
                <Text style={{ flex: 1 }} variant="bodySmall">
                  {spec.label}
                </Text>
                <Text variant="bodySmall">
                  {spec.value} {spec.unit ?? ''}
                  {!spec.verified ? ' *' : ''}
                </Text>
              </View>
            ))}
            {dossier.keySpecs.some((spec) => !spec.verified) && (
              <Text variant="bodySmall" style={{ opacity: 0.6, marginTop: 6 }}>
                * {t('unverified_value')}
              </Text>
            )}
          </Card.Content>
        </Card>
      )}

      {(dossier.recentFailures ?? []).length > 0 && (
        <Card style={styles.card}>
          <Card.Title title={t('recent_failures')} />
          <Card.Content>
            {dossier.recentFailures.slice(0, 5).map((failure) => (
              <View key={failure.id} style={styles.row}>
                <Text style={{ flex: 1 }} variant="bodySmall">
                  {failure.name ?? failure.code}
                </Text>
                <Text variant="bodySmall">
                  {failure.occurredAt
                    ? new Date(failure.occurredAt).toLocaleDateString()
                    : ''}
                </Text>
              </View>
            ))}
          </Card.Content>
        </Card>
      )}

      <View style={{ height: 24 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: { margin: 10, marginBottom: 0 },
  actionRow: { flexDirection: 'row', paddingHorizontal: 10, paddingTop: 10, gap: 10 },
  action: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: 3 },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 8, gap: 6 },
  chip: { marginRight: 6, marginBottom: 6 },
  staleChip: { margin: 10, marginBottom: 0, alignSelf: 'flex-start' }
});
