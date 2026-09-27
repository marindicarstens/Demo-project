import { Page } from '@playwright/test';

/** New Account's "1. Your details" step. */
export class NewAccountForm {
  constructor(private readonly page: Page) {}

  async fill(details: { fullName: string; email: string; phone: string }): Promise<void> {
    await this.page.getByLabel('Full name').fill(details.fullName);
    await this.page.getByLabel('Email').fill(details.email);
    await this.page.getByLabel('Phone').fill(details.phone);
  }

  async continue(): Promise<void> {
    await this.page.getByRole('button', { name: 'Continue' }).click();
  }

  async submit(details: { fullName: string; email: string; phone: string }): Promise<void> {
    await this.fill(details);
    await this.continue();
  }
}
