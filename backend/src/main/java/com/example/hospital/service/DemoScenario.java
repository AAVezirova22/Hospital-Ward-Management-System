package com.example.hospital.service;

/** Synthetic 28-patient demonstration scenario, separate from first-run admin bootstrap. */
public final class DemoScenario {
  private DemoScenario() {}

  public static final String[][] DOCTORS = {
    {"Elena", "Dimitrova", "Internal medicine"},
    {"Martin", "Ivanov", "Cardiology"},
    {"Nadia", "Petrova", "Neurology"}
  };

  public static final String[][] PATIENTS = {
    {"Ivan", "Petrov"},
    {"Mila", "Georgieva"},
    {"Alexander", "Kolev"},
    {"Sofia", "Ivanova"},
    {"Daniel", "Stoyanov"},
    {"Eva", "Nikolova"},
    {"Boris", "Dimitrov"},
    {"Anna", "Todorova"},
    {"Stefan", "Marinov"},
    {"Maria", "Popova"},
    {"Nikolai", "Vasilev"},
    {"Vera", "Angelova"},
    {"Lilia", "Hristova"},
    {"Pavel", "Dobrev"},
    {"Irina", "Mihaylova"},
    {"Victor", "Radev"},
    {"Daria", "Ilieva"},
    {"Emil", "Kostov"},
    {"Yana", "Pavlova"},
    {"Radoslav", "Dinev"},
    {"Elitsa", "Yordanova"},
    {"Kalin", "Atanasov"},
    {"Nina", "Borisova"},
    {"Todor", "Zhelev"},
    {"Raya", "Stankova"},
    {"Plamen", "Nedev"},
    {"Alina", "Markova"},
    {"Georgi", "Velikov"}
  };

  public static final String[] PROCEDURES = {
    "Complete blood count", "Electrocardiogram", "Ultrasound examination", "Chest X-ray"
  };
  public static final String[] PROCEDURE_COSTS = {"27.40", "46.80", "83.50", "68.20"};
}
