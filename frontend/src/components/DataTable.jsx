import React from 'react';

const DataTable = ({ columns, data, emptyMessage = 'No data available', loading = false }) => {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.4)' }}>
            {columns.map((col, i) => (
              <th key={i} style={{ padding: '0.75rem 1rem', color: 'var(--text-secondary)', fontWeight: '600', fontSize: '0.875rem' }}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={columns.length} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                Loading...
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, rowIndex) => (
              <tr 
                key={rowIndex} 
                style={{ 
                  borderBottom: rowIndex === data.length - 1 ? 'none' : '1px solid rgba(255, 255, 255, 0.2)',
                  transition: 'background-color 0.2s'
                }}
                className="hover-row"
              >
                {columns.map((col, colIndex) => (
                  <td key={colIndex} style={{ padding: '0.75rem 1rem', fontSize: '0.875rem', color: 'var(--text-primary)' }}>
                    {col.render ? col.render(row) : row[col.accessor]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
      <style>{`
        .hover-row:hover {
          background-color: rgba(255, 255, 255, 0.2);
        }
      `}</style>
    </div>
  );
};

export default DataTable;
