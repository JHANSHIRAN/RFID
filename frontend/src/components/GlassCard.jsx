import React from 'react';

const GlassCard = ({ children, className = '', title, action, style }) => {
  return (
    <div className={`glass-card ${className}`} style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', height: '100%', ...style }}>
      {(title || action) && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          {title && <h3 style={{ margin: 0 }}>{title}</h3>}
          {action && <div>{action}</div>}
        </div>
      )}
      <div style={{ flex: 1 }}>
        {children}
      </div>
    </div>
  );
};

export default GlassCard;
