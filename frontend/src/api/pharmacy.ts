import apiClient from './axios';

export interface PharmacyItem {
  id: string;
  itemName?: string;
  medicineName?: string;
  genericName?: string;
  description?: string;
  category: string;
  dosageForm?: string;
  strength?: string;
  unitPrice: number;
  stockQuantity: number;
  reorderLevel?: number;
  reorderThreshold?: number;
  batchNumber?: string;
  expiryDate?: string;
  manufacturer?: string;
  lowStock?: boolean;
  expired?: boolean;
  expiringSoon?: boolean;
}

export const pharmacyApi = {
  getInventory: (page = 0, size = 20) => apiClient.get('/billing/pharmacy/inventory', { params: { page, size } }),
  getLowStock: () => apiClient.get('/billing/pharmacy/low-stock'),
  getExpiringSoon: () => apiClient.get('/billing/pharmacy/expiring-soon'),
  addItem: (data: Partial<PharmacyItem>) => apiClient.post('/billing/pharmacy/inventory', data),
  dispense: (data: { itemId: string; quantity: number; patientId?: string; patientName?: string; autoAddToBill?: boolean; appointmentId?: string }) => apiClient.post('/billing/pharmacy/dispense', data),
  restock: (data: { itemId: string; quantity: number; costPrice: number }) => apiClient.post('/billing/pharmacy/restock', data),
};
