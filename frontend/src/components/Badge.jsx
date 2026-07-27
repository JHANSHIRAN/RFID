import React from 'react';

const Badge = ({ children, type = 'default' }) => {
  const styles = {
    default: { bg: 'rgba(100, 116, 139, 0.2)', color: 'var(--text-secondary)' },
    success: { bg: 'rgba(16, 185, 129, 0.2)', color: '#059669' },
    warning: { bg: 'rgba(245, 158, 11, 0.2)', color: '#D97706' },
    danger: { bg: 'rgba(239, 68, 68, 0.2)', color: '#DC2626' },
    primary: { bg: 'rgba(59, 130, 246, 0.2)', color: 'var(--accent-primary)' }
  };

  const style = styles[type] || styles.default;

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: '0.25rem 0.75rem',
      borderRadius: '9999px',
      fontSize: '0.75rem',
      fontWeight: '600',
      backgroundColor: style.bg,
      color: style.color,
      whiteSpace: 'nowrap'
    }}>
      {children}
    </span>
  );
};

export default Badge;
