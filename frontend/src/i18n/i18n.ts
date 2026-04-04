import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import locale from './translations/en';
import esJSON from './translations/es';
import { FlagComponent } from 'country-flag-icons/react/1x1';
import { ES, US } from 'country-flag-icons/react/3x2';
import { LocaleSingularArg } from '@fullcalendar/react';
import esLocale from '@fullcalendar/core/locales/es';
import enLocale from '@fullcalendar/core/locales/en-gb';
import { Locale as DateLocale } from 'date-fns';
import { es, enUS } from 'date-fns/locale';

const resources = {
  en: { translation: locale },
  es: { translation: esJSON }
};

i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: 'en',
    keySeparator: false,
    fallbackLng: 'en',
    react: {
      useSuspense: true
    },
    interpolation: {
      escapeValue: false
    }
  });

export type SupportedLanguage = 'EN' | 'ES';

export const supportedLanguages: {
  code: Lowercase<SupportedLanguage>;
  label: string;
  Icon: FlagComponent;
  calendarLocale: LocaleSingularArg;
  dateLocale: DateLocale;
}[] = [
  {
    code: 'en',
    label: 'English',
    Icon: US,
    calendarLocale: enLocale,
    dateLocale: enUS
  },
  {
    code: 'es',
    label: 'Spanish',
    Icon: ES,
    calendarLocale: esLocale,
    dateLocale: es
  }
];

export default i18n;
