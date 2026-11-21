#!/usr/bin/env python3
"""Rebuild API request examples using only the Python standard library.

The CSV supplies lane numbers and quotes. City pairs are taken, in lane-number
order, from the assignment PDF. The PDF's second test case is transcribed below.
Run this file from any working directory with Python 3.9 or newer.
"""

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CITY_PAIRS = [
    ("Mumbai", "Delhi"),
    ("Delhi", "Bangalore"),
    ("Chennai", "Kolkata"),
    ("Pune", "Hyderabad"),
    ("Ahmedabad", "Jaipur"),
    ("Guwahati", "Shillong"),
    ("Lucknow", "Patna"),
    ("Ranchi", "Bhubaneswar"),
    ("Surat", "Indore"),
    ("Nagpur", "Raipur"),
]


def lanes_for(city_pairs):
    return [
        {"id": index, "origin": origin, "destination": destination}
        for index, (origin, destination) in enumerate(city_pairs, start=1)
    ]


def transporter(identifier, name, quotes):
    return {
        "id": identifier,
        "name": name,
        "laneQuotes": [
            {"laneId": lane_id, "quote": quote} for lane_id, quote in quotes
        ],
    }


def from_csv():
    with (ROOT / "transporter_assignment_data.csv").open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != ["Trade Lane", "T1", "T2", "T3", "T4", "T5", "T6", "T7"]:
            raise ValueError("Expected the supplied CSV headers: Trade Lane and T1 through T7.")
        rows = list(reader)

    expected_lanes = [f"Lane {index}" for index in range(1, 10)]
    if [row["Trade Lane"].strip() for row in rows] != expected_lanes:
        raise ValueError("Expected the supplied nine lanes in lane-number order.")

    return {
        "lanes": lanes_for(CITY_PAIRS[:len(rows)]),
        "transporters": [
            transporter(
                identifier,
                f"Transporter T{identifier}",
                [(index, int(row[f"T{identifier}"].strip())) for index, row in enumerate(rows, start=1)],
            )
            for identifier in range(1, 8)
        ],
    }


def main():
    csv_input = from_csv()
    case_1 = {
        "lanes": csv_input["lanes"][:5],
        "transporters": [
            {**entry, "laneQuotes": entry["laneQuotes"][:5]}
            for entry in csv_input["transporters"]
        ],
    }
    case_2 = {
        "lanes": lanes_for([
            ("Chandigarh", "Shimla"),
            ("Agra", "Kanpur"),
            ("Varanasi", "Gorakhpur"),
            ("Amritsar", "Ludhiana"),
            ("Coimbatore", "Madurai"),
        ]),
        "transporters": [
            transporter(1, "Transporter X", [(1, 1000), (2, 1500)]),
            transporter(2, "Transporter Y", [(3, 2000), (4, 1800)]),
            transporter(3, "Transporter Z", [(5, 1200)]),
            transporter(4, "Transporter W", [(1, 1100), (4, 1750)]),
        ],
    }
    sparse_input = {
        "lanes": lanes_for(CITY_PAIRS),
        "transporters": [
            transporter(1, "Transporter T1", [(1, 5000), (2, 6000)]),
            transporter(2, "Transporter T2", [(3, 7000), (4, 4000)]),
            transporter(3, "Transporter T3", [(5, 3000), (6, 2000)]),
            transporter(4, "Transporter T4", [(7, 8000), (8, 5000)]),
            transporter(5, "Transporter T5", [(9, 4500), (10, 3500)]),
            transporter(6, "Transporter T6", [(1, 5500), (2, 6200)]),
            transporter(7, "Transporter T7", [(3, 7500), (4, 4500)]),
        ],
    }
    examples = {
        "csv-input.json": csv_input,
        "case-1-input.json": case_1,
        "case-2-input.json": case_2,
        "sparse-input.json": sparse_input,
    }
    directory = ROOT / "examples"
    directory.mkdir(parents=True, exist_ok=True)
    for name, payload in examples.items():
        (directory / name).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print("Generated four API request examples. Maven packages these for Swagger.")


if __name__ == "__main__":
    main()
