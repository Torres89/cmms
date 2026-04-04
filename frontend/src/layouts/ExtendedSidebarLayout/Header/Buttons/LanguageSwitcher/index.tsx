import { IconButton, styled, Tooltip } from '@mui/material';
import internationalization, { supportedLanguages } from 'src/i18n/i18n';
import { useTranslation } from 'react-i18next';

const IconButtonWrapper = styled(IconButton)(
  ({ theme }) => `
        width: ${theme.spacing(6)};
        height: ${theme.spacing(6)};

        svg {
          width: 28px;
        }
`
);

function LanguageSwitcher() {
  const { i18n, t } = useTranslation();
  const currentLang = i18n.language;

  const currentSupportedLanguage = supportedLanguages.find(
    (lang) => lang.code === currentLang
  );

  const toggleLanguage = () => {
    const newLang = currentLang === 'en' ? 'es' : 'en';
    internationalization.changeLanguage(newLang);
  };

  return (
    <Tooltip arrow title={t('Language Switcher')}>
      <IconButtonWrapper color="secondary" onClick={toggleLanguage}>
        {currentSupportedLanguage && (
          <currentSupportedLanguage.Icon
            title={currentSupportedLanguage.label}
          />
        )}
      </IconButtonWrapper>
    </Tooltip>
  );
}

export default LanguageSwitcher;
