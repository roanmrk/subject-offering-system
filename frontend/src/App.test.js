import { render, screen } from '@testing-library/react';
import App from './App';

test('renders landing page without crashing', () => {
  render(<App />);
  expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
});